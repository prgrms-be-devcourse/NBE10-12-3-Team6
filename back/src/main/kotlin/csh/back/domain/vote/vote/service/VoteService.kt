package csh.back.domain.vote.vote.service

import csh.back.domain.trip.chat.service.ChatService
import csh.back.domain.trip.event.dto.TripEvent
import csh.back.domain.trip.event.enums.TripEventType
import csh.back.domain.trip.event.service.TripEventService
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.exception.NonMemberException
import csh.back.domain.trip.group.exception.NotFoundException
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.group.settings.repository.TripGroupSettingsRepository
import csh.back.domain.trip.member.repository.TripMemberRepository
import csh.back.domain.trip.place.service.TripPlaceService
import csh.back.domain.trip.timeline.entity.Timeline
import csh.back.domain.trip.timeline.repository.TimelineRepository
import csh.back.domain.vote.item.repository.VoteItemRepository
import csh.back.domain.vote.user.repository.VoteUserRepository
import csh.back.domain.vote.vote.dto.response.VoteCreateResponse
import csh.back.domain.vote.vote.dto.response.VoteFindListResponse
import csh.back.domain.vote.vote.dto.response.VoteFindResponse
import csh.back.domain.vote.vote.dto.response.VoteFindUserResponse
import csh.back.domain.vote.vote.dto.response.VoteFindWithUpdateCountResponse
import csh.back.domain.vote.vote.dto.response.VoteWithTimelineResponse
import csh.back.domain.vote.vote.dto.response.VoterResponse
import csh.back.domain.vote.vote.dto.web.VoteTimelineResponse
import csh.back.domain.vote.vote.entity.Vote
import csh.back.domain.vote.vote.enums.VoteStatus
import csh.back.domain.vote.vote.repository.VoteRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class VoteService(
    private val voteRepository: VoteRepository,
    private val voteItemRepository: VoteItemRepository,
    private val voteUserRepository: VoteUserRepository,
    private val tripGroupRepository: TripGroupRepository,
    private val tripGroupSettingsRepository: TripGroupSettingsRepository,
    private val timelineRepository: TimelineRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val tripPlaceService: TripPlaceService,
    private val tripEventService: TripEventService,
    private val chatService: ChatService,
) {

    fun findVoteList(tripGroupId: Long, memberId: Long): List<VoteFindListResponse> {
        val tripGroup = validateTripMember(tripGroupId, memberId)
        val totalDays = tripGroup.nights + 1
        val byDay = timelineRepository.findAllByTripGroupId(tripGroupId)
            .groupBy { it.dayNumber }
        val byTimelineId = voteRepository.findVotesWithTimelineByTripGroupId(tripGroupId)
            .associateBy { it.timeline.id }

        val voteFindListResponses = mutableListOf<VoteFindListResponse>()
        for (day in 1..totalDays) {
            val timelineResponses = byDay.getOrDefault(day.toLong(), emptyList())
                .map { timeline -> createVoteAndTimelineResponse(timeline, byTimelineId) }
            voteFindListResponses.add(
                VoteFindListResponse.of(tripGroup.startDate.plusDays((day - 1).toLong()), timelineResponses),
            )
        }
        return voteFindListResponses
    }

    fun findVoteItemAndCount(tripGroupId: Long, voteId: Long, memberId: Long): VoteFindWithUpdateCountResponse {
        validateTripMember(tripGroupId, memberId)
        val tripMember = tripMemberRepository.findByMemberIdAndTripGroupId(memberId, tripGroupId)
            .orElseThrow(::RuntimeException)
        val voteUser = voteUserRepository.findByVoteIdAndTripMemberId(voteId, tripMember.id!!).orElse(null)
        val voteItemList = voteItemRepository.findAllByVoteIdWithTripPlace(voteId)
        val countMap = voteCount(voteId)
        val vote = voteRepository.findById(voteId).orElseThrow(::RuntimeException)
        val votersByVoteItemId: Map<Long?, List<VoterResponse>> = if (vote.isAnonymous) {
            emptyMap()
        } else {
            voteUserRepository.findAllByVoteIdWithVoter(voteId)
                .groupBy({ it.voteItem.id }, { VoterResponse(it.tripMember.id!!, it.tripMember.member.name) })
        }
        val voteItem = voteUser?.voteItem
        val updateCount = voteUser?.updateCount ?: DEFAULT_UPDATE_COUNT
        val voteFindResponses = voteItemList.map { vi ->
            VoteFindResponse.of(
                vi.tripPlace.id,
                vi.tripPlace.name,
                countMap.getOrDefault(vi.id, 0L),
                vi == voteItem,
                votersByVoteItemId.getOrDefault(vi.id, emptyList()),
            )
        }
        val wishPlaceFindResponses = tripPlaceService.findWishPlaces(tripGroupId, memberId)
        return VoteFindWithUpdateCountResponse.of(voteFindResponses, wishPlaceFindResponses, updateCount, vote)
    }

    fun findAllVoteIds(timelineIds: List<Long>): Map<Long, Long> {
        val voteTimelineIdProjections = voteRepository.findVoteIdsByTimelineIds(timelineIds)
        return voteTimelineIdProjections.associate { it.getTimelineId() to it.getVoteId() }
    }

    fun findUserVoteThisPlace(tripGroupId: Long, voteId: Long, tripPlaceId: Long, memberId: Long): List<VoteFindUserResponse> {
        validateTripMember(tripGroupId, memberId)
        val voteItem = voteItemRepository.findByVoteIdAndTripPlaceId(voteId, tripPlaceId).orElseThrow(::RuntimeException)
        val voteUsers = voteUserRepository.findByVoteItemId(voteItem.id!!)
        return voteUsers.map(VoteFindUserResponse::from)
    }

    @Transactional
    fun wrapperCreateVote(tripGroupId: Long, memberId: Long, timelineId: Long): VoteCreateResponse {
        val timeline = timelineRepository.findById(timelineId).orElseThrow(::RuntimeException)
        val response = createVote(tripGroupId, memberId, timeline)
        tripEventService.publishAfterCommit(
            TripEvent(
                eventType = TripEventType.VOTE_CREATED,
                message = "${timeline.dayNumber}일차 시간 구간 투표 생성",
                tripGroupId = tripGroupId,
                actorMemberId = memberId,
                dayNumber = timeline.dayNumber,
                timelineId = timelineId,
                voteId = requireNotNull(response.voteId),
            ),
        )
        chatService.recordSystemMessage(tripGroupId, "${timeline.dayNumber}일차 시간 구간 투표 생성")
        return response
    }

    @Transactional
    fun createVote(tripGroupId: Long, memberId: Long, timeline: Timeline): VoteCreateResponse {
        val tripGroup = validateTripMember(tripGroupId, memberId)
        val tripMember = tripMemberRepository.findByMemberIdAndTripGroupId(memberId, tripGroupId).orElseThrow(::RuntimeException)
        val expireTime = tripGroup.startDate.minusDays(1).atStartOfDay()
        val isAnonymous = resolveIsAnonymous(tripGroupId)
        val vote = Vote(tripGroup = tripGroup, timeline = timeline, tripMember = tripMember, expireTime = expireTime, isAnonymous = isAnonymous)
        val saved = voteRepository.save(vote)
        return VoteCreateResponse.from(saved)
    }

    @Transactional
    fun createVoteBatch(tripGroupId: Long, memberId: Long, timelines: List<Timeline>) {
        val tripGroup = validateTripMember(tripGroupId, memberId)
        val expireTime = tripGroup.startDate.minusDays(1).atStartOfDay()
        val tripMember = tripMemberRepository.findByMemberIdAndTripGroupId(memberId, tripGroupId).orElseThrow(::RuntimeException)
        val isAnonymous = resolveIsAnonymous(tripGroupId)
        val votes = timelines.map { timeline ->
            Vote(tripGroup = tripGroup, timeline = timeline, tripMember = tripMember, expireTime = expireTime, isAnonymous = isAnonymous)
        }
        voteRepository.saveAll(votes)
    }

    private fun resolveIsAnonymous(tripGroupId: Long): Boolean =
        tripGroupSettingsRepository.findByTripGroupId(tripGroupId).map { it.isAnonymousVote }.orElse(true)

    @Transactional
    fun lockPendingVote(voteId: Long): Vote {
        val vote = voteRepository.findByIdWithLock(voteId).orElseThrow(::RuntimeException)
        check(vote.status == VoteStatus.PENDING) { "이미 확정되었거나 만료된 투표입니다." }
        return vote
    }

    @Transactional
    fun updateAnonymous(tripGroupId: Long, voteId: Long, memberId: Long, isAnonymous: Boolean): Boolean {
        validateTripAdmin(tripGroupId, memberId)
        val vote = voteRepository.findById(voteId).orElseThrow(::RuntimeException)
        check(vote.status == VoteStatus.PENDING) { "이미 확정되었거나 만료된 투표입니다." }
        vote.updateAnonymous(isAnonymous)
        return vote.isAnonymous
    }

    private fun validateTripAdmin(tripGroupId: Long, memberId: Long) {
        val isAdmin = tripMemberRepository.existsByTripGroupIdAndMemberIdAndIsAdminTrue(tripGroupId, memberId)
        require(isAdmin) { "여행 모임 방장만 접근할 수 있습니다." }
    }

    fun voteConfirm(maxVoteItemId: Long, voteId: Long): VoteTimelineResponse {
        val voteItem = voteItemRepository.findById(maxVoteItemId).orElseThrow(::RuntimeException)
        val confirmPlaceId = voteItem.tripPlace.id!!
        val timeline = voteRepository.findTimelineByVoteId(voteId).orElseThrow(::RuntimeException)
        voteItem.vote.updateStatus(VoteStatus.CONFIRMED)
        return VoteTimelineResponse.of(confirmPlaceId, timeline)
    }

    fun voteCount(voteId: Long): Map<Long, Long> =
        voteUserRepository.countGroupByVoteId(voteId).associate { row -> row.getVoteItemId() to row.getVoteCount() }

    fun expireVote(voteId: Long) {
        val vote = voteRepository.findById(voteId).orElseThrow(::RuntimeException)
        vote.updateStatus(VoteStatus.EXPIRED)
    }

    private fun createVoteAndTimelineResponse(timeline: Timeline, byTimelineId: Map<Long?, Vote>): VoteWithTimelineResponse {
        val timelineId = timeline.id
        val vote = byTimelineId[timelineId]
        if (vote == null) {
            log.error("Timeline {}과 연결된 Vote가 존재 하지 않습니다! 확인 해주세요!", timelineId)
            return VoteWithTimelineResponse.of(timeline, null)
        }
        return VoteWithTimelineResponse.of(timeline, vote)
    }

    private fun validateTripMember(tripGroupId: Long, memberId: Long): TripGroup {
        val tripGroup = tripGroupRepository.findById(tripGroupId)
            .orElseThrow {
                NotFoundException("존재하지 않는 모임입니다.")
            }

        if (!tripMemberRepository.existsByTripGroupIdAndMemberId(tripGroupId, memberId)) {
            throw NonMemberException("해당 모임의 멤버가 아닙니다.")
        }

        return tripGroup
    }

    companion object {
        private val log = LoggerFactory.getLogger(VoteService::class.java)
        private const val DEFAULT_UPDATE_COUNT = 0
    }
}
