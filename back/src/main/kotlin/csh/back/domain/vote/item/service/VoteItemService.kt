package csh.back.domain.vote.item.service

import csh.back.domain.trip.member.validator.TripMemberValidator
import csh.back.domain.trip.event.dto.TripEvent
import csh.back.domain.trip.event.enums.TripEventType
import csh.back.domain.trip.event.service.TripEventService
import csh.back.domain.trip.place.repository.TripPlaceRepository
import csh.back.domain.vote.item.entity.VoteItem
import csh.back.domain.vote.item.repository.VoteItemRepository
import csh.back.domain.vote.user.dto.response.VoteUserSaveResponseDto
import csh.back.domain.vote.user.service.VoteUserService
import csh.back.domain.vote.vote.repository.VoteRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class VoteItemService(
    private val voteItemRepository: VoteItemRepository,
    private val voteRepository: VoteRepository,
    private val tripPlaceRepository: TripPlaceRepository,
    private val voteUserService: VoteUserService,
    private val tripMemberValidator: TripMemberValidator,
    private val tripEventService: TripEventService,
) {

    @Transactional
    fun saveVoteItem(tripGroupId: Long, memberId: Long, voteId: Long, tripPlaceId: Long): VoteUserSaveResponseDto {
        log.info("장소 아이디 값 : {}", tripPlaceId.toString())
        log.info("투표 아이디 값 : {}", voteId.toString())
        tripMemberValidator.validMember(tripGroupId, memberId)

        val vote = voteRepository.findById(voteId).orElseThrow(::RuntimeException)
        val tripPlace = tripPlaceRepository.findById(tripPlaceId).orElseThrow(::RuntimeException)
        val voteItem = voteItemRepository.findByVoteIdAndTripPlaceId(voteId, tripPlaceId).orElse(null)

        val response = if (voteItem == null) {
            val saved = voteItemRepository.save(VoteItem(vote = vote, tripPlace = tripPlace))
            voteUserService.saveVoteUser(saved, tripGroupId, memberId)
        } else {
            voteItem.updateTripPlace(tripPlace)
            voteUserService.saveVoteUser(voteItem, tripGroupId, memberId)
        }

        tripEventService.publishAfterCommit(
            TripEvent(
                eventType = TripEventType.VOTE_PARTICIPATION_UPDATED,
                message = "${vote.timeline.dayNumber}일차 시간 구간의 투표 현황이 변경되었습니다.",
                tripGroupId = tripGroupId,
                actorMemberId = memberId,
                dayNumber = vote.timeline.dayNumber,
                timelineId = requireNotNull(vote.timeline.id),
                voteId = voteId,
                tripPlaceId = tripPlaceId,
            ),
        )
        return response
    }

    companion object {
        private val log = LoggerFactory.getLogger(VoteItemService::class.java)
    }
}
