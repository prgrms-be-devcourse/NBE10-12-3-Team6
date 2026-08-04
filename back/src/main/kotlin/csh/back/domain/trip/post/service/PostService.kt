package csh.back.domain.trip.post.service

import csh.back.domain.member.dto.response.AuthFilterDto
import csh.back.domain.trip.group.service.TripGroupService
import csh.back.domain.trip.member.repository.TripMemberRepository
import csh.back.domain.trip.member.validator.TripMemberValidator
import csh.back.domain.trip.post.dto.request.UpdatePostRequest
import csh.back.domain.trip.post.dto.response.PostCursorResponse
import csh.back.domain.trip.post.dto.response.PostResponse
import csh.back.domain.trip.post.dto.response.PostTimelineResponse
import csh.back.domain.trip.post.dto.response.PostsDailyResponse
import csh.back.domain.trip.post.entity.Post
import csh.back.domain.trip.post.event.PostDeletedEvent
import csh.back.domain.trip.post.like.repository.PostLikeRepository
import csh.back.domain.trip.post.reminder.repository.PostReminderRepository
import csh.back.domain.trip.post.repository.PostRepository
import csh.back.domain.trip.timeline.entity.Timeline
import csh.back.domain.trip.timeline.repository.TimelineRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.nio.charset.StandardCharsets
import java.util.Base64

@Service
@Transactional(readOnly = true)
class PostService(
    private val postRepository: PostRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val timelineRepository: TimelineRepository,
    private val s3UploadService: S3UploadService,
    private val postImageProcessor: PostImageProcessor,
    private val tripMemberValidator: TripMemberValidator,
    private val tripGroupService: TripGroupService,
    private val postLikeRepository: PostLikeRepository,
    private val postReminderRepository: PostReminderRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    fun getPosts(
        tripGroupId: Long,
        memberId: Long,
        cursor: String?,
        size: Int
    ): PostCursorResponse {
        require(size in 1..MAX_PAGE_SIZE) {
            "사진은 한 번에 최대 ${MAX_PAGE_SIZE}개까지 조회할 수 있습니다."
        }
        tripMemberValidator.validMember(tripGroupId, memberId)

        val tripGroup =
            tripGroupService.findTripGroupById(tripGroupId)

        val tripMembers =
            tripMemberRepository.findByTripGroupId(
                tripGroup.id!!
            )

        val pageRequest = PageRequest.of(0, size + 1)
        val decodedCursor = cursor?.let(::decodeCursor)
        val fetchedPosts = if (decodedCursor == null) {
            postRepository.findFirstPageWithTimelineAndPlaceByAuthorIn(
                tripMembers,
                pageRequest
            )
        } else {
            postRepository.findNextPageWithTimelineAndPlaceByAuthorIn(
                tripMembers,
                decodedCursor.createdAt,
                decodedCursor.postId,
                pageRequest
            )
        }
        val hasNext = fetchedPosts.size > size
        val posts = fetchedPosts.take(size)

        val schedulesByDate =
            timelineRepository
                .findByTripGroupIdSorted(tripGroupId)
                .groupBy {
                    it.startTime.toLocalDate()
                }

        val likeCounts =
            findLikeCounts(posts)

        val groups = posts
            .groupBy {
                it.createdAt!!.toLocalDate()
            }
            .toSortedMap()
            .map { (date, dailyPosts) ->
                val daySchedules =
                    schedulesByDate[date].orEmpty()

                PostsDailyResponse(
                    date = date,
                    posts = dailyPosts.map { post ->
                        toSummaryWithSlot(
                            post = post,
                            daySchedules = daySchedules,
                            likeCount =
                                likeCounts[post.id] ?: 0L
                        )
                    }
                )
            }

        return PostCursorResponse(
            groups = groups,
            nextCursor = if (hasNext) {
                posts.lastOrNull()?.let(::encodeCursor)
            } else {
                null
            },
            hasNext = hasNext
        )
    }

    fun getPost(
        tripGroupId: Long,
        postId: Long
    ): PostResponse {
        val post =
            postRepository.findById(postId)
                .orElseThrow {
                    IllegalArgumentException(
                        "게시글이 존재하지 않습니다."
                    )
                }

        validateTripGroup(post, tripGroupId)

        val likeCount =
            postLikeRepository.countByPostId(postId)

        return PostResponse.from(
            post = post,
            likeCount = likeCount
        )
    }

    @Transactional
    fun update(
        tripGroupId: Long,
        postId: Long,
        memberId: Long,
        request: UpdatePostRequest
    ) {
        requireValidContent(request.content)
        findAuthorizedPost(
            tripGroupId,
            postId,
            memberId
        ).update(request.content)
    }

    @Transactional
    fun delete(
        tripGroupId: Long,
        postId: Long
    ) {
        val post = findAuthorizedPost(
            tripGroupId,
            postId
        )
        val imageUrls = listOfNotNull(
            post.contentUrl,
            post.normalContentUrl,
            post.dataSaverContentUrl
        ).distinct()

        postLikeRepository.deleteAllByPostId(postId)
        postReminderRepository.deleteAllByPostId(postId)
        postRepository.delete(post)
        eventPublisher.publishEvent(PostDeletedEvent(imageUrls))
    }

    @Transactional
    fun create(
        tripGroupId: Long,
        memberId: Long,
        timelineId: Long?,
        image: MultipartFile?,
        content: String?
    ): PostResponse {
        requireValidContent(content)
        tripMemberValidator.validMember(
            tripGroupId,
            memberId
        )

        val author =
            tripMemberRepository
                .findByMemberIdAndTripGroupId(
                    memberId,
                    tripGroupId
                )
                .orElseThrow {
                    IllegalArgumentException(
                        "여행 멤버가 존재하지 않습니다."
                    )
                }

        val timeline =
            timelineId?.let {
                timelineRepository
                    .findById(it)
                    .orElse(null)
            }

        if (
            timeline != null &&
            timeline.tripGroup.id != tripGroupId
        ) {
            throw IllegalArgumentException(
                "해당 여행의 타임라인이 아닙니다."
            )
        }

        val hasImage =
            image != null &&
                    !image.isEmpty

        var dominantColor: String? = null
        val uploadedImages =
            if (hasImage) {
                val originalImage = requireNotNull(image)
                val variants = postImageProcessor.createVariants(originalImage)
                dominantColor = variants.dominantColor
                s3UploadService.uploadImages(originalImage, variants)
            } else {
                null
            }

        val savedPost = try {
            postRepository.saveAndFlush(
                Post(
                    author = author,
                    timeline = timeline,
                    type =
                        if (hasImage) {
                            "IMAGE"
                        } else {
                            "TEXT"
                        },
                    originalFilename = uploadedImages?.originalFilename,
                    contentUrl = uploadedImages?.originalUrl,
                    normalContentUrl = uploadedImages?.normalUrl,
                    dataSaverContentUrl = uploadedImages?.dataSaverUrl,
                    dominantColor = dominantColor,
                    content = content?.trim()?.ifEmpty { null }
                )
            )
        } catch (exception: RuntimeException) {
            s3UploadService.deleteImages(
                uploadedImages?.originalUrl,
                uploadedImages?.normalUrl,
                uploadedImages?.dataSaverUrl
            )
            throw exception
        }
        return PostResponse.from(savedPost)
    }

    private fun requireValidContent(content: String?) {
        require(content == null || content.length <= 20) {
            "20자 까지 입력이 가능합니다."
        }
    }

    fun getCurrentSlot(
        tripGroupId: Long,
        memberId: Long,
        dayNumber: Int
    ): PostTimelineResponse {
        val now = LocalDateTime.now()

        val dayStart = now.toLocalDate().atStartOfDay()

        val dayEnd = dayStart.plusDays(1)

        val tripMember =
            tripMemberRepository
                .findByMemberIdAndTripGroupId(
                    memberId,
                    tripGroupId
                )
                .orElseThrow {
                    IllegalArgumentException(
                        "여행 멤버가 존재하지 않습니다."
                    )
                }

        val todayPosts =
            postRepository
                .findByAuthorIdAndCreatedAtBetween(
                    tripMember.id!!,
                    dayStart,
                    dayEnd
                )

        val schedules =
            timelineRepository
                .findByTripAndDateSorted(
                    tripGroupId,
                    dayNumber.toLong()
                )
        val current =
            schedules.firstOrNull {
                !now.isBefore(it.startTime) &&
                        now.isBefore(it.endTime)
            }

        val slotStart: LocalDateTime
        val slotEnd: LocalDateTime
        val timelineId: Long?
        val confirmedPlaceName: String?

        if (current != null) {
            slotStart = current.startTime
            slotEnd = current.endTime
            timelineId = current.id
            confirmedPlaceName =
                current.tripWishPlace?.name
        } else {
            slotStart =
                calcEmptySlotStart(
                    now,
                    schedules
                )

            slotEnd =
                calcEmptySlotEnd(
                    slotStart,
                    schedules
                )

            timelineId = null
            confirmedPlaceName = null
        }

        val isTaken =
            todayPosts.any {
                !it.createdAt!!.isBefore(slotStart) &&
                        it.createdAt!!.isBefore(slotEnd)
            }

        return PostTimelineResponse(
            slotStart,
            slotEnd,
            timelineId,
            confirmedPlaceName,
            isTaken
        )
    }

    private fun toSummaryWithSlot(
        post: Post,
        daySchedules: List<Timeline>,
        likeCount: Long
    ): PostsDailyResponse.PostSummary {
        val timeline =
            post.timeline

        if (timeline != null) {
            return PostsDailyResponse.PostSummary(
                postId = post.id,
                originalFilename = post.originalFilename,
                contentUrl = post.contentUrl,
                normalContentUrl = post.normalContentUrl ?: post.contentUrl,
                dataSaverContentUrl = post.dataSaverContentUrl
                    ?: post.normalContentUrl
                    ?: post.contentUrl,
                dominantColor = post.dominantColor,
                timelineId = timeline.id,
                startTime = timeline.startTime,
                endTime = timeline.endTime,
                confirmedPlaceName =
                    timeline.tripWishPlace?.name,
                createdAt = post.createdAt,
                likeCount = likeCount,
                authorMemberId = requireNotNull(post.author.member.id),
                content = post.content
            )
        }

        val captured =
            post.createdAt!!

        val slotStart =
            calcSlotStart(
                captured,
                daySchedules
            )

        return PostsDailyResponse.PostSummary(
            postId = post.id,
            originalFilename = post.originalFilename,
            contentUrl = post.contentUrl,
            normalContentUrl = post.normalContentUrl ?: post.contentUrl,
            dataSaverContentUrl = post.dataSaverContentUrl
                ?: post.normalContentUrl
                ?: post.contentUrl,
            dominantColor = post.dominantColor,
            timelineId = null,
            startTime = slotStart,
            endTime =
                calcSlotEnd(
                    slotStart,
                    daySchedules
                ),
            confirmedPlaceName = null,
            createdAt = captured,
            likeCount = likeCount,
            authorMemberId = requireNotNull(post.author.member.id),
            content = post.content
        )
    }

    private fun findLikeCounts(posts: List<Post>): Map<Long, Long> {
        val postIds =
            posts.mapNotNull {
                it.id
            }
        if (postIds.isEmpty()) {
            return emptyMap()
        }
        return postLikeRepository
            .countGroupByPostId(postIds)
            .associate { row ->
                val postId =
                    (row[0] as Number)
                        .toLong()

                val likeCount =
                    (row[1] as Number)
                        .toLong()

                postId to likeCount
            }
    }

    private fun calcSlotStart(
        time: LocalDateTime,
        schedules: List<Timeline>
    ): LocalDateTime {
        val hourFloor =
            time.truncatedTo(
                ChronoUnit.HOURS
            )

        val lastScheduleEnd =
            schedules
                .map {
                    it.endTime
                }
                .filter {
                    !it.isAfter(time)
                }
                .maxOrNull()
                ?: hourFloor

        return maxOf(
            hourFloor,
            lastScheduleEnd
        )
    }

    private fun calcSlotEnd(
        slotStart: LocalDateTime,
        schedules: List<Timeline>
    ): LocalDateTime {
        val end =
            if (isOnTheHour(slotStart)) {
                slotStart.plusHours(1)
            } else {
                slotStart
                    .truncatedTo(
                        ChronoUnit.HOURS
                    )
                    .plusHours(1)
            }
        val nextScheduleStart =
            schedules
                .map {
                    it.startTime
                }
                .filter {
                    it.isAfter(slotStart)
                }
                .minOrNull()
                ?: end

        return minOf(
            end,
            nextScheduleStart
        )
    }

    private fun isOnTheHour(time: LocalDateTime)
    : Boolean =
        time.minute == 0 &&
                time.second == 0 &&
                time.nano == 0

    private fun calcEmptySlotStart(now: LocalDateTime, schedules: List<Timeline>)
    : LocalDateTime =
        calcSlotStart(
            now,
            schedules
        )

    private fun calcEmptySlotEnd(slotStart: LocalDateTime, schedules: List<Timeline>): LocalDateTime =
        calcSlotEnd(
            slotStart,
            schedules
        )

    private fun currentMemberId(): Long {
        val authentication =
            SecurityContextHolder
                .getContext()
                .authentication
                ?: throw IllegalStateException(
                    "로그인이 필요합니다."
                )

        val principal =
            authentication.principal as? AuthFilterDto
                ?: throw IllegalStateException(
                    "인증된 회원 정보를 찾을 수 없습니다."
                )

        return principal.id
    }

    private fun validateAuthor(post: Post, memberId: Long) {
        if (
            post.author.member.id !=
            memberId
        ) {
            throw IllegalArgumentException(
                "작성자만 수정 및 삭제할 수 있습니다."
            )
        }
    }

    private fun validateTripGroup(post: Post, tripGroupId: Long) {
        val actualTripGroupId =
            post.timeline?.tripGroup?.id
                ?: post.author.tripGroup.id

        if (
            actualTripGroupId !=
            tripGroupId
        ) {
            throw IllegalArgumentException(
                "해당 여행의 게시글이 아닙니다."
            )
        }
    }

    private fun findAuthorizedPost(
        tripGroupId: Long,
        postId: Long,
        memberId: Long = currentMemberId()
    ): Post {
        val post =
            postRepository.findById(postId)
                .orElseThrow {
                    IllegalArgumentException(
                        "게시글이 존재하지 않습니다."
                    )
                }

        validateTripGroup(post, tripGroupId)

        validateAuthor(post, memberId)
        return post
    }

    private fun encodeCursor(post: Post): String {
        val cursorValue = "${requireNotNull(post.createdAt)}|${requireNotNull(post.id)}"
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(cursorValue.toByteArray(StandardCharsets.UTF_8))
    }

    private fun decodeCursor(cursor: String): PostCursor = try {
        val decoded = String(
            Base64.getUrlDecoder().decode(cursor),
            StandardCharsets.UTF_8
        )
        val parts = decoded.split('|', limit = 2)
        require(parts.size == 2)
        PostCursor(
            createdAt = LocalDateTime.parse(parts[0]),
            postId = parts[1].toLong()
        )
    } catch (exception: RuntimeException) {
        throw IllegalArgumentException("올바르지 않은 사진 조회 커서입니다.", exception)
    }

    private data class PostCursor(
        val createdAt: LocalDateTime,
        val postId: Long
    )

    companion object {
        private const val MAX_PAGE_SIZE = 10
    }
}
