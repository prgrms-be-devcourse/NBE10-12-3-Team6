package csh.back.domain.trip.timeline.service

import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.member.repository.TripMemberRepository
import csh.back.domain.trip.member.validator.TripMemberValidator
import csh.back.domain.trip.place.entity.TripPlace
import csh.back.domain.trip.place.repository.TripPlaceRepository
import csh.back.domain.trip.timeline.dto.request.TimelineAllCreateRequest
import csh.back.domain.trip.timeline.dto.request.TimelineCreateRequest
import csh.back.domain.trip.timeline.dto.request.TimelineUpdateRequest
import csh.back.domain.trip.timeline.dto.response.TimelineCountResponse
import csh.back.domain.trip.timeline.dto.response.TimelineResponse
import csh.back.domain.trip.timeline.dto.response.TimelineWithVoteIdResponse
import csh.back.domain.trip.timeline.entity.Timeline
import csh.back.domain.trip.timeline.repository.TimelineRepository
import csh.back.domain.vote.vote.dto.response.VoteConfirmResponse
import csh.back.domain.vote.vote.enums.VoteStatus
import csh.back.domain.vote.vote.repository.VoteRepository
import csh.back.domain.vote.vote.service.VoteService
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.ThreadLocalRandom

@Service
@Transactional
class TimelineService(
    private val timelineRepository: TimelineRepository,
    private val tripGroupRepository: TripGroupRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val tripPlaceRepository: TripPlaceRepository,
    private val voteService: VoteService,
    private val timelineEventService: TimelineEventService,
    private val tripMemberValidator: TripMemberValidator,
    private val entityManager: EntityManager,
    private val voteRepository: VoteRepository,
) {

    fun createTimeline(
        tripId: Long,
        memberId: Long,
        request: TimelineCreateRequest,
    ): TimelineResponse {
        validateTripAdmin(tripId, memberId)
        validateStartAndEndTime(request.startTime, request.endTime)
        validateTimelineOverlap(
            tripId = tripId,
            dayNumber = request.dayNumber,
            startTime = request.startTime,
            endTime = request.endTime,
        )

        val timeline = Timeline.create(
            tripGroup = findTripGroup(tripId),
            dayNumber = request.dayNumber,
            startTime = request.startTime,
            endTime = request.endTime,
        )
        val savedTimeline = timelineRepository.save(timeline)

        voteService.createVote(tripId, memberId, savedTimeline)
        timelineEventService.sendTimelineUpdatedEventAfterCommit(tripId, memberId)

        return TimelineResponse.from(savedTimeline)
    }

    fun createAllTimelines(
        tripId: Long,
        memberId: Long,
        request: TimelineAllCreateRequest,
    ): List<TimelineResponse> {
        validateTripAdmin(tripId, memberId)
        validateSameDayNumber(request)
        request.timelines.forEach { timeline ->
            validateStartAndEndTime(timeline.startTime, timeline.endTime)
        }
        validateTimelineRange(request)
        request.timelines.forEach { timeline ->
            validateTimelineOverlap(
                tripId = tripId,
                dayNumber = timeline.dayNumber,
                startTime = timeline.startTime,
                endTime = timeline.endTime,
            )
        }

        val tripGroup = findTripGroup(tripId)
        val timelines = request.timelines.map { timeline ->
            Timeline.create(
                tripGroup = tripGroup,
                dayNumber = timeline.dayNumber,
                startTime = timeline.startTime,
                endTime = timeline.endTime,
            )
        }
        val savedTimelines = timelineRepository.saveAll(timelines)

        voteService.createVoteBatch(tripId, memberId, savedTimelines)
        timelineEventService.sendTimelineUpdatedEventAfterCommit(tripId, memberId)

        return savedTimelines.map(TimelineResponse::from)
    }

    @Transactional(readOnly = true)
    fun getTimelines(
        tripId: Long,
        memberId: Long,
        dayNumber: Long,
    ): List<TimelineWithVoteIdResponse> {
        validateTripMember(tripId, memberId)
        require(dayNumber >= MINIMUM_DAY) {
            "일차는 $MINIMUM_DAY 이상이어야 합니다."
        }

        val timelines = timelineRepository
            .findByTripGroupIdAndDayNumberOrderByStartTimeAsc(tripId, dayNumber)
        val timelineIds = timelines.map { timeline ->
            requireNotNull(timeline.id)
        }
        val timelineVoteMap = voteService.findAllVoteIds(timelineIds)

        return timelines.map { timeline ->
            TimelineWithVoteIdResponse.of(
                timeline = timeline,
                voteId = timelineVoteMap[timeline.id],
            )
        }
    }

    @Transactional(readOnly = true)
    fun getTimelineCounts(
        tripId: Long,
        memberId: Long,
    ): List<TimelineCountResponse> {
        validateTripMember(tripId, memberId)

        return timelineRepository.countGroupByDayNumberId(tripId)
            .map { row ->
                TimelineCountResponse.of(
                    day = row[0] as Long,
                    count = row[1] as Long,
                )
            }
    }

    fun updateTimeline(
        tripId: Long,
        timelineId: Long,
        memberId: Long,
        request: TimelineUpdateRequest,
    ): TimelineResponse {
        validateTripMember(tripId, memberId)
        lockTripGroup(tripId)
        validateStartAndEndTime(request.startTime, request.endTime)

        val timeline = findTimeline(tripId, timelineId)
        validateTimelineOverlapForUpdate(
            tripId = tripId,
            timelineId = timelineId,
            dayNumber = timeline.dayNumber,
            startTime = request.startTime,
            endTime = request.endTime,
        )

        timeline.updateTimeRange(request.startTime, request.endTime)
        timelineEventService.sendTimelineUpdatedEventAfterCommit(tripId, memberId)

        return TimelineResponse.from(timeline)
    }

    fun deleteTimeline(
        tripId: Long,
        timelineId: Long,
        memberId: Long,
    ) {
        validateTripAdmin(tripId, memberId)

        val timeline = findTimeline(tripId, timelineId)
        deleteVotesByTimeline(timelineId)
        timelineRepository.delete(timeline)
        timelineEventService.sendTimelineUpdatedEventAfterCommit(tripId, memberId)
    }

    fun confirmVote(
        tripId: Long,
        memberId: Long,
        voteId: Long,
    ): VoteConfirmResponse {
        tripMemberValidator.validMember(tripId, memberId)

        val countMap: Map<Long, Long> = voteService.voteCount(voteId)
        val maxCount = countMap.values.maxOrNull()
            ?: throw NoSuchElementException("투표 결과가 없습니다.")
        val maxKeys = countMap
            .filterValues { count -> count == maxCount }
            .keys
            .toList()
        val isTie = maxKeys.size != 1
        val maxVoteItemId = selectWinner(maxKeys)

        val voteTimelineResponse = voteService.voteConfirm(maxVoteItemId, voteId)
        val confirmPlaceId = voteTimelineResponse.confirmPlaceId
        confirmPlaceByHost(voteTimelineResponse.timeLine, tripId, confirmPlaceId)
        timelineEventService.sendTimelineUpdatedEventAfterCommit(tripId, memberId)

        return VoteConfirmResponse.of(
            VoteStatus.CONFIRMED.nickname,
            confirmPlaceId,
            isTie,
        )
    }

    fun expireAndConfirmBySystem(voteId: Long) {
        val countMap: Map<Long, Long> = voteService.voteCount(voteId)
        val maxCount = countMap.values.maxOrNull()

        if (maxCount == null || maxCount == 0L) {
            voteService.expireVote(voteId)
            return
        }

        val maxKeys = countMap
            .filterValues { count -> count == maxCount }
            .keys
            .toList()
        val winnerVoteItemId = selectWinner(maxKeys)
        val voteTimelineResponse = voteService.voteConfirm(winnerVoteItemId, voteId)

        confirmPlaceBySystem(
            timeline = voteTimelineResponse.timeLine,
            tripWishPlaceId = voteTimelineResponse.confirmPlaceId,
        )
    }

    private fun selectWinner(candidateIds: List<Long>): Long =
        if (candidateIds.size == 1) {
            candidateIds.first()
        } else {
            candidateIds[ThreadLocalRandom.current().nextInt(candidateIds.size)]
        }

    private fun confirmPlaceBySystem(
        timeline: Timeline,
        tripWishPlaceId: Long,
    ) {
        val tripWishPlace = tripPlaceRepository.findById(tripWishPlaceId)
            .orElseThrow {
                IllegalStateException("확정 장소 없음: $tripWishPlaceId")
            }
        timeline.updateTripWishPlace(tripWishPlace)
    }

    private fun confirmPlaceByHost(
        timeline: Timeline,
        tripId: Long,
        tripWishPlaceId: Long,
    ) {
        val tripWishPlace = tripPlaceRepository
            .findByIdAndTripGroupId(tripWishPlaceId, tripId)
            .orElseThrow {
                IllegalArgumentException("확정된 장소가 없습니다.")
            }
        timeline.updateTripWishPlace(tripWishPlace)
    }

    @Suppress("unused")
    private fun resolveTripIdByVoteId(voteId: Long): Long {
        val timeline = voteRepository.findTimeLineByVoteId(voteId)
            .orElseThrow {
                IllegalStateException("Timeline 없음: voteId=$voteId")
            }
        return requireNotNull(timeline.tripGroup.id)
    }

    private fun deleteVotesByTimeline(timelineId: Long) {
        entityManager.createQuery(
            """
            delete from VoteUser vu
            where vu.vote.id in (
                select v.id from Vote v
                where v.timeline.id = :timelineId
            )
            """.trimIndent(),
        )
            .setParameter("timelineId", timelineId)
            .executeUpdate()

        entityManager.createQuery(
            """
            delete from VoteItem vi
            where vi.vote.id in (
                select v.id from Vote v
                where v.timeline.id = :timelineId
            )
            """.trimIndent(),
        )
            .setParameter("timelineId", timelineId)
            .executeUpdate()

        entityManager.createQuery(
            """
            delete from Vote v
            where v.timeline.id = :timelineId
            """.trimIndent(),
        )
            .setParameter("timelineId", timelineId)
            .executeUpdate()
    }

    private fun lockTripGroup(tripId: Long) {
        tripGroupRepository.findByIdWithLock(tripId)
            .orElseThrow {
                IllegalArgumentException("여행 모임을 찾을 수 없습니다.")
            }
    }

    private fun findTripGroup(tripId: Long): TripGroup =
        tripGroupRepository.findById(tripId)
            .orElseThrow {
                IllegalArgumentException("여행 모임을 찾을 수 없습니다.")
            }

    private fun validateTripMember(tripId: Long, memberId: Long) {
        val isMember = tripMemberRepository
            .existsByTripGroupIdAndMemberId(tripId, memberId)

        require(isMember) {
            "여행 모임 멤버만 접근할 수 있습니다."
        }
    }

    private fun findTimeline(tripId: Long, timelineId: Long): Timeline =
        timelineRepository.findByIdAndTripGroupId(timelineId, tripId)
            .orElseThrow {
                IllegalArgumentException("타임라인을 찾을 수 없습니다.")
            }

    private fun validateTripAdmin(tripId: Long, memberId: Long) {
        val isAdmin = tripMemberRepository
            .existsByTripGroupIdAndMemberIdAndIsAdminTrue(tripId, memberId)

        require(isAdmin) {
            "여행 모임 방장만 접근할 수 있습니다."
        }
    }

    private fun validateSameDayNumber(request: TimelineAllCreateRequest) {
        request.timelines.forEach { timeline ->
            require(request.dayNumber == timeline.dayNumber) {
                "일차 정보가 일치하지 않습니다."
            }
        }
    }

    private fun validateStartAndEndTime(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
    ) {
        require(startTime.isBefore(endTime)) {
            "종료 시간은 시작 시간보다 늦어야 합니다."
        }
    }

    private fun validateTimelineRange(request: TimelineAllCreateRequest) {
        request.timelines.forEachIndexed { index, currentTimeline ->
            request.timelines
                .drop(index + 1)
                .forEach { nextTimeline ->
                    val isOverlapped =
                        currentTimeline.startTime.isBefore(nextTimeline.endTime) &&
                            currentTimeline.endTime.isAfter(nextTimeline.startTime)

                    require(!isOverlapped) {
                        "시간 카테고리가 겹치면 안 됩니다."
                    }
                }
        }
    }

    private fun validateTimelineOverlap(
        tripId: Long,
        dayNumber: Long,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
    ) {
        val overlapCount = timelineRepository.countOverlappingTimeline(
            tripId = tripId,
            dayNumber = dayNumber,
            startTime = startTime,
            endTime = endTime,
        )

        require(overlapCount == 0L) {
            "시간 카테고리가 겹치면 안 됩니다."
        }
    }

    private fun validateTimelineOverlapForUpdate(
        tripId: Long,
        timelineId: Long,
        dayNumber: Long,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
    ) {
        val overlapCount = timelineRepository.countOverlappingTimelineExceptSelf(
            tripId = tripId,
            dayNumber = dayNumber,
            timelineId = timelineId,
            startTime = startTime,
            endTime = endTime,
        )

        require(overlapCount == 0L) {
            "시간 카테고리가 겹치면 안 됩니다."
        }
    }

    companion object {
        private const val MINIMUM_DAY = 1L
    }
}
