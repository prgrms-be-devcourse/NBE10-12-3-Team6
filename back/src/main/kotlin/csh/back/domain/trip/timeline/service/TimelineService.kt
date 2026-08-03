package csh.back.domain.trip.timeline.service

import csh.back.domain.trip.event.dto.TripEvent
import csh.back.domain.trip.event.enums.TripEventType
import csh.back.domain.trip.event.service.TripEventService
import csh.back.domain.trip.chat.service.ChatService
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.exception.NonMemberException
import csh.back.domain.trip.group.exception.NotFoundException
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.member.repository.TripMemberRepository
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
    private val tripEventService: TripEventService,
    private val entityManager: EntityManager,
    private val voteRepository: VoteRepository,
    private val timelineFreeTimeService: TimelineFreeTimeService,
    private val chatService: ChatService,
) {

    fun createTimeline(
        tripGroupId: Long,
        memberId: Long,
        request: TimelineCreateRequest,
    ): TimelineResponse {
        validateTripAdmin(tripGroupId, memberId)
        validateStartAndEndTime(request.startTime, request.endTime)
        validateTimelineOverlap(
            tripGroupId = tripGroupId,
            dayNumber = request.dayNumber,
            startTime = request.startTime,
            endTime = request.endTime,
        )

        val timeline = Timeline.create(
            tripGroup = findTripGroup(tripGroupId),
            dayNumber = request.dayNumber,
            startTime = request.startTime,
            endTime = request.endTime,
        )
        val savedTimeline = timelineRepository.save(timeline)

        voteService.createVote(tripGroupId, memberId, savedTimeline)
        tripEventService.publishAfterCommit(
            TripEvent(
                eventType = TripEventType.TIMELINE_CREATED,
                message = "${savedTimeline.dayNumber}일차 시간 구간 추가",
                tripGroupId = tripGroupId,
                actorMemberId = memberId,
                dayNumber = savedTimeline.dayNumber,
                timelineId = requireNotNull(savedTimeline.id),
            ),
        )
        chatService.recordSystemMessage(tripGroupId, "${savedTimeline.dayNumber}일차 시간 구간 추가")

        return TimelineResponse.from(savedTimeline)
    }

    fun createAllTimelines(
        tripGroupId: Long,
        memberId: Long,
        request: TimelineAllCreateRequest,
    ): List<TimelineResponse> {
        validateTripAdmin(tripGroupId, memberId)
        validateSameDayNumber(request)
        request.timelines.forEach { timeline ->
            validateStartAndEndTime(timeline.startTime, timeline.endTime)
        }
        validateTimelineRange(request)
        request.timelines.forEach { timeline ->
            validateTimelineOverlap(
                tripGroupId = tripGroupId,
                dayNumber = timeline.dayNumber,
                startTime = timeline.startTime,
                endTime = timeline.endTime,
            )
        }

        val tripGroup = findTripGroup(tripGroupId)
        val timelines = request.timelines.map { timeline ->
            Timeline.create(
                tripGroup = tripGroup,
                dayNumber = timeline.dayNumber,
                startTime = timeline.startTime,
                endTime = timeline.endTime,
            )
        }
        val savedTimelines = timelineRepository.saveAll(timelines)

        voteService.createVoteBatch(tripGroupId, memberId, savedTimelines)
        tripEventService.publishAfterCommit(
            TripEvent(
                eventType = TripEventType.TIMELINE_BATCH_CREATED,
                message = "${request.dayNumber}일차 시간 구간 추가",
                tripGroupId = tripGroupId,
                actorMemberId = memberId,
                dayNumber = request.dayNumber,
                timelineIds = savedTimelines.map { timeline ->
                    requireNotNull(timeline.id)
                },
            ),
        )
        chatService.recordSystemMessage(tripGroupId, "${request.dayNumber}일차 시간 구간 추가")

        return savedTimelines.map(TimelineResponse::from)
    }

    fun getTimelines(
        tripGroupId: Long,
        memberId: Long,
        dayNumber: Long,
    ): List<TimelineWithVoteIdResponse> {
        validateTripMember(tripGroupId, memberId)
        require(dayNumber >= MINIMUM_DAY) {
            "일차는 $MINIMUM_DAY 이상이어야 합니다."
        }

        timelineFreeTimeService.createForToday(tripGroupId)
        val timelines = timelineRepository
            .findByTripGroupIdAndDayNumberOrderByStartTimeAsc(tripGroupId, dayNumber)
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

    fun getTimelineCounts(
        tripGroupId: Long,
        memberId: Long,
    ): List<TimelineCountResponse> {
        validateTripMember(tripGroupId, memberId)
        timelineFreeTimeService.createForToday(tripGroupId)

        return timelineRepository.countGroupByDayNumberId(tripGroupId)
            .map { row ->
                TimelineCountResponse.of(
                    day = row[0] as Long,
                    count = row[1] as Long,
                )
            }
    }

    fun updateTimeline(
        tripGroupId: Long,
        timelineId: Long,
        memberId: Long,
        request: TimelineUpdateRequest,
    ): TimelineResponse {
        validateTripMember(tripGroupId, memberId)
        lockTripGroup(tripGroupId)
        validateStartAndEndTime(request.startTime, request.endTime)

        val timeline = findTimeline(tripGroupId, timelineId)
        validateTimelineOverlapForUpdate(
            tripGroupId = tripGroupId,
            timelineId = timelineId,
            dayNumber = timeline.dayNumber,
            startTime = request.startTime,
            endTime = request.endTime,
        )

        timeline.updateTimeRange(request.startTime, request.endTime)
        tripEventService.publishAfterCommit(
            TripEvent(
                eventType = TripEventType.TIMELINE_TIME_UPDATED,
                message = "${timeline.dayNumber}일차 시간 구간 시간 변경",
                tripGroupId = tripGroupId,
                actorMemberId = memberId,
                dayNumber = timeline.dayNumber,
                timelineId = timelineId,
            ),
        )
        chatService.recordSystemMessage(tripGroupId, "${timeline.dayNumber}일차 시간 구간 시간 변경")

        return TimelineResponse.from(timeline)
    }

    fun deleteTimeline(
        tripGroupId: Long,
        timelineId: Long,
        memberId: Long,
    ) {
        validateTripAdmin(tripGroupId, memberId)

        val timeline = findTimeline(tripGroupId, timelineId)
        val dayNumber = timeline.dayNumber
        deleteVotesByTimeline(timelineId)
        timelineRepository.delete(timeline)
        tripEventService.publishAfterCommit(
            TripEvent(
                eventType = TripEventType.TIMELINE_DELETED,
                message = "${dayNumber}일차 시간 구간 삭제",
                tripGroupId = tripGroupId,
                actorMemberId = memberId,
                dayNumber = dayNumber,
                timelineId = timelineId,
            ),
        )
        chatService.recordSystemMessage(tripGroupId, "${dayNumber}일차 시간 구간 삭제")
    }

    fun confirmVote(
        tripGroupId: Long,
        memberId: Long,
        voteId: Long,
    ): VoteConfirmResponse {
        validateTripMember(tripGroupId, memberId)
        voteService.lockPendingVote(voteId)

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
        val timeline = voteTimelineResponse.timeline
        val tripWishPlace = confirmPlaceByHost(timeline, tripGroupId, confirmPlaceId)
        tripEventService.publishAfterCommit(
            TripEvent(
                eventType = TripEventType.TIMELINE_PLACE_CONFIRMED,
                message = "${timeline.dayNumber}일차 ${timeline.startTime.hour}시 ${tripWishPlace.name} 확정!",
                tripGroupId = tripGroupId,
                actorMemberId = memberId,
                dayNumber = timeline.dayNumber,
                timelineId = requireNotNull(timeline.id),
                voteId = voteId,
                tripPlaceId = confirmPlaceId,
            ),
        )
        chatService.recordSystemMessage(tripGroupId, "${timeline.dayNumber}일차 ${timeline.startTime.hour}시 ${tripWishPlace.name} 확정!")

        return VoteConfirmResponse.of(
            VoteStatus.CONFIRMED.nickname,
            confirmPlaceId,
            isTie,
        )
    }

    fun expireAndConfirmBySystem(voteId: Long) {
        voteService.lockPendingVote(voteId)

        val countMap: Map<Long, Long> = voteService.voteCount(voteId)
        val maxCount = countMap.values.maxOrNull()

        if (maxCount == null || maxCount == 0L) {
            val timeline = findTimelineByVoteId(voteId)
            voteService.expireVote(voteId)
            tripEventService.publishAfterCommit(
                TripEvent(
                    eventType = TripEventType.VOTE_EXPIRED,
                    message = "${timeline.dayNumber}일차 시간 구간 투표 종료",
                    tripGroupId = requireNotNull(timeline.tripGroup.id),
                    actorMemberId = null,
                    dayNumber = timeline.dayNumber,
                    timelineId = requireNotNull(timeline.id),
                    voteId = voteId,
                ),
            )
            chatService.recordSystemMessage(requireNotNull(timeline.tripGroup.id), "${timeline.dayNumber}일차 시간 구간 투표 종료")
            return
        }

        val maxKeys = countMap
            .filterValues { count -> count == maxCount }
            .keys
            .toList()
        val winnerVoteItemId = selectWinner(maxKeys)
        val voteTimelineResponse = voteService.voteConfirm(winnerVoteItemId, voteId)

        val timeline = voteTimelineResponse.timeline
        val tripWishPlace = confirmPlaceBySystem(
            timeline = timeline,
            tripWishPlaceId = voteTimelineResponse.confirmPlaceId,
        )
        tripEventService.publishAfterCommit(
            TripEvent(
                eventType = TripEventType.TIMELINE_PLACE_CONFIRMED,
                message = "${timeline.dayNumber}일차 ${timeline.startTime.hour}시 ${tripWishPlace.name} 확정!",
                tripGroupId = requireNotNull(timeline.tripGroup.id),
                actorMemberId = null,
                dayNumber = timeline.dayNumber,
                timelineId = requireNotNull(timeline.id),
                voteId = voteId,
                tripPlaceId = voteTimelineResponse.confirmPlaceId,
            ),
        )
        chatService.recordSystemMessage(requireNotNull(timeline.tripGroup.id), "${timeline.dayNumber}일차 ${timeline.startTime.hour}시 ${tripWishPlace.name} 확정!")
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
    ): TripPlace {
        val tripWishPlace = tripPlaceRepository.findById(tripWishPlaceId)
            .orElseThrow {
                IllegalStateException("확정 장소 없음: $tripWishPlaceId")
            }
        timeline.updateTripWishPlace(tripWishPlace)
        return tripWishPlace
    }

    private fun confirmPlaceByHost(
        timeline: Timeline,
        tripGroupId: Long,
        tripWishPlaceId: Long,
    ): TripPlace {
        val tripWishPlace = tripPlaceRepository
            .findByIdAndTripGroupId(tripWishPlaceId, tripGroupId)
            .orElseThrow {
                IllegalArgumentException("확정된 장소가 없습니다.")
            }
        timeline.updateTripWishPlace(tripWishPlace)
        return tripWishPlace
    }

    private fun findTimelineByVoteId(voteId: Long): Timeline =
        voteRepository.findTimelineByVoteId(voteId)
            .orElseThrow {
                IllegalStateException("Timeline 없음: voteId=$voteId")
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

    private fun lockTripGroup(tripGroupId: Long) {
        tripGroupRepository.findByIdWithLock(tripGroupId)
            .orElseThrow {
                NotFoundException("존재하지 않는 모임입니다.")
            }
    }

    private fun findTripGroup(tripGroupId: Long): TripGroup =
        tripGroupRepository.findById(tripGroupId)
            .orElseThrow {
                NotFoundException("존재하지 않는 모임입니다.")
            }

    private fun validateTripMember(tripGroupId: Long, memberId: Long) {
        if (!tripGroupRepository.existsById(tripGroupId)) {
            throw NotFoundException("존재하지 않는 모임입니다.")
        }

        val isMember = tripMemberRepository
            .existsByTripGroupIdAndMemberId(tripGroupId, memberId)

        if (!isMember) {
            throw NonMemberException("해당 모임의 멤버가 아닙니다.")
        }
    }

    private fun findTimeline(tripGroupId: Long, timelineId: Long): Timeline =
        timelineRepository.findByIdAndTripGroupId(timelineId, tripGroupId)
            .orElseThrow {
                NotFoundException("존재하지 않는 타임라인입니다.")
            }

    private fun validateTripAdmin(tripGroupId: Long, memberId: Long) {
        validateTripMember(tripGroupId, memberId)

        val isAdmin = tripMemberRepository
            .existsByTripGroupIdAndMemberIdAndIsAdminTrue(tripGroupId, memberId)

        if (!isAdmin) {
            throw NonMemberException("여행 모임 방장만 접근할 수 있습니다.")
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
        tripGroupId: Long,
        dayNumber: Long,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
    ) {
        val overlapCount = timelineRepository.countOverlappingTimeline(
            tripGroupId = tripGroupId,
            dayNumber = dayNumber,
            startTime = startTime,
            endTime = endTime,
        )

        require(overlapCount == 0L) {
            "시간 카테고리가 겹치면 안 됩니다."
        }
    }

    private fun validateTimelineOverlapForUpdate(
        tripGroupId: Long,
        timelineId: Long,
        dayNumber: Long,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
    ) {
        val overlapCount = timelineRepository.countOverlappingTimelineExceptSelf(
            tripGroupId = tripGroupId,
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
