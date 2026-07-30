package csh.back.domain.trip.post.service

import csh.back.domain.member.dto.response.AuthFilterDto
import csh.back.domain.trip.group.service.TripGroupService
import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.member.repository.TripMemberRepository
import csh.back.domain.trip.member.validator.TripMemberValidator
import csh.back.domain.trip.post.dto.request.UpdatePostRequest
import csh.back.domain.trip.post.dto.response.PostResponse
import csh.back.domain.trip.post.dto.response.PostTimelineResponse
import csh.back.domain.trip.post.dto.response.PostsDailyResponse
import csh.back.domain.trip.post.entity.Post
import csh.back.domain.trip.post.repository.PostRepository
import csh.back.domain.trip.timeline.entity.Timeline
import csh.back.domain.trip.timeline.repository.TimelineRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
@Transactional(readOnly = true)
class PostService(
    private val postRepository: PostRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val timelineRepository: TimelineRepository,
    private val s3UploadService: S3UploadService,
    private val tripMemberValidator: TripMemberValidator,
    private val tripGroupService: TripGroupService
) {
    fun getPosts(tripGroupId: Long, memberId: Long): List<PostsDailyResponse> {
        tripMemberValidator.validMember(tripGroupId, memberId)

        val tripGroup = tripGroupService.findTripGroupById(tripGroupId)
        val tripMembers = tripMemberRepository.findByTripGroupId(tripGroup.id!!)
        val posts = postRepository.findWithTimelineAndPlaceByAuthorIdIn(tripMembers)
        val schedulesByDate = timelineRepository.findByTripGroupIdSorted(tripGroupId)
            .groupBy { it.startTime.toLocalDate() }

        return posts
            .groupBy { it.createdAt!!.toLocalDate() }
            .toSortedMap()
            .map { (date, dailyPosts) ->
                val daySchedules = schedulesByDate[date].orEmpty()
                PostsDailyResponse(
                    date = date,
                    posts = dailyPosts.map { toSummaryWithSlot(it, daySchedules) }
                )
            }
    }

    fun getPost(tripGroupId: Long, postId: Long): PostResponse {
        val post = postRepository.findById(postId)
            .orElseThrow { IllegalArgumentException("게시글이 존재하지 않습니다.") }
        validateTripGroup(post, tripGroupId)
        return PostResponse.from(post)
    }

    @Transactional
    fun update(tripGroupId: Long, postId: Long, request: UpdatePostRequest) {
        findAuthorizedPost(tripGroupId, postId).update(request.content)
    }

    @Transactional
    fun delete(tripGroupId: Long, postId: Long) {
        postRepository.delete(findAuthorizedPost(tripGroupId, postId))
    }

    @Transactional
    fun create(
        tripGroupId: Long,
        memberId: Long,
        timelineId: Long?,
        image: MultipartFile?
    ): PostResponse {
        tripMemberValidator.validMember(tripGroupId, memberId)

        val author = tripMemberRepository.findByMemberIdAndTripGroupId(memberId, tripGroupId)
            .orElseThrow { IllegalArgumentException("여행 멤버가 존재하지 않습니다.") }
        val timeline = timelineId?.let { timelineRepository.findById(it).orElse(null) }
        if (timeline != null && timeline.tripGroup.id != tripGroupId) {
            throw IllegalArgumentException("해당 여행의 타임라인이 아닙니다.")
        }

        val hasImage = image != null && !image.isEmpty
        val imageUrl = if (hasImage) s3UploadService.uploadImage(image) else null

        val savedPost = postRepository.save(
            Post(
                author = author,
                timeline = timeline,
                type = if (hasImage) "IMAGE" else "TEXT",
                contentUrl = imageUrl,
                content = null
            )
        )
        return PostResponse.from(savedPost)
    }

    fun getCurrentSlot(tripGroupId: Long, memberId: Long, dayNumber: Int): PostTimelineResponse {
        val now = LocalDateTime.now()
        val dayStart = now.toLocalDate().atStartOfDay()
        val dayEnd = dayStart.plusDays(1)
        val tripMember = tripMemberRepository.findByMemberIdAndTripGroupId(memberId, tripGroupId)
            .orElseThrow { IllegalArgumentException("여행 멤버가 존재하지 않습니다.") }
        val todayPosts = postRepository.findByAuthorIdAndCreatedAtBetween(tripMember.id!!, dayStart, dayEnd)
        val schedules = timelineRepository.findByTripAndDateSorted(tripGroupId, dayNumber.toLong())
        val current = schedules.firstOrNull { !now.isBefore(it.startTime) && now.isBefore(it.endTime) }

        val slotStart: LocalDateTime
        val slotEnd: LocalDateTime
        val timelineId: Long?
        val confirmedPlaceName: String?

        if (current != null) {
            slotStart = current.startTime
            slotEnd = current.endTime
            timelineId = current.id
            confirmedPlaceName = current.tripWishPlace?.name
        } else {
            slotStart = calcEmptySlotStart(now, schedules)
            slotEnd = calcEmptySlotEnd(slotStart, schedules)
            timelineId = null
            confirmedPlaceName = null
        }

        val isTaken = todayPosts.any {
            !it.createdAt!!.isBefore(slotStart) && it.createdAt!!.isBefore(slotEnd)
        }
        return PostTimelineResponse(slotStart, slotEnd, timelineId, confirmedPlaceName, isTaken)
    }

    private fun toSummaryWithSlot(post: Post, daySchedules: List<Timeline>): PostsDailyResponse.PostSummary {
        val timeline = post.timeline
        if (timeline != null) {
            return PostsDailyResponse.PostSummary(
                postId = post.id,
                contentUrl = post.contentUrl,
                timelineId = timeline.id,
                startTime = timeline.startTime,
                endTime = timeline.endTime,
                confirmedPlaceName = timeline.tripWishPlace?.name,
                createdAt = post.createdAt
            )
        }

        val captured = post.createdAt!!
        val slotStart = calcSlotStart(captured, daySchedules)
        return PostsDailyResponse.PostSummary(
            postId = post.id,
            contentUrl = post.contentUrl,
            timelineId = null,
            startTime = slotStart,
            endTime = calcSlotEnd(slotStart, daySchedules),
            confirmedPlaceName = null,
            createdAt = captured
        )
    }

    private fun calcSlotStart(time: LocalDateTime, schedules: List<Timeline>): LocalDateTime {
        val hourFloor = time.truncatedTo(ChronoUnit.HOURS)
        val lastScheduleEnd = schedules.map { it.endTime }
            .filter { !it.isAfter(time) }
            .maxOrNull() ?: hourFloor
        return maxOf(hourFloor, lastScheduleEnd)
    }

    private fun calcSlotEnd(slotStart: LocalDateTime, schedules: List<Timeline>): LocalDateTime {
        val end = if (isOnTheHour(slotStart)) slotStart.plusHours(1)
        else slotStart.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        val nextScheduleStart = schedules.map { it.startTime }
            .filter { it.isAfter(slotStart) }
            .minOrNull() ?: end
        return minOf(end, nextScheduleStart)
    }

    private fun isOnTheHour(time: LocalDateTime): Boolean =
        time.minute == 0 && time.second == 0 && time.nano == 0

    private fun calcEmptySlotStart(now: LocalDateTime, schedules: List<Timeline>): LocalDateTime =
        calcSlotStart(now, schedules)

    private fun calcEmptySlotEnd(slotStart: LocalDateTime, schedules: List<Timeline>): LocalDateTime =
        calcSlotEnd(slotStart, schedules)

    private fun currentMemberId(): Long {
        val authentication = SecurityContextHolder
            .getContext()
            .authentication
            ?: throw IllegalStateException("로그인이 필요합니다.")

        val principal = authentication.principal as? AuthFilterDto
            ?: throw IllegalStateException("올바르지 않은 인증 정보입니다.")

        return principal.id
    }

    private fun validateAuthor(post: Post) {
        if (post.author.member.id != currentMemberId()) {
            throw IllegalArgumentException("작성자만 수정 및 삭제할 수 있습니다.")
        }
    }

    private fun validateTripGroup(post: Post, tripGroupId: Long) {
        val actualTripGroupId = post.timeline?.tripGroup?.id ?: post.author.tripGroup.id
        if (actualTripGroupId != tripGroupId) {
            throw IllegalArgumentException("해당 여행의 게시글이 아닙니다.")
        }
    }

    private fun findAuthorizedPost(tripGroupId: Long, postId: Long): Post {
        val post = postRepository.findById(postId)
            .orElseThrow { IllegalArgumentException("게시글이 존재하지 않습니다.") }
        validateTripGroup(post, tripGroupId)
        validateAuthor(post)
        return post
    }
}
