package csh.back.domain.vote.item.service

import csh.back.domain.trip.member.validator.TripMemberValidator
import csh.back.domain.trip.place.repository.TripPlaceRepository
import csh.back.domain.vote.item.entity.VoteItem
import csh.back.domain.vote.item.repository.VoteItemRepository
import csh.back.domain.vote.user.dto.response.VoteUserSaveResponseDto
import csh.back.domain.vote.user.service.VoteUserService
import csh.back.domain.vote.vote.repository.VoteRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
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
) {

    @Transactional
    fun saveVoteItem(tripGroupId: Long, memberId: Long, voteId: Long, tripPlaceId: Long): VoteUserSaveResponseDto {
        log.info("장소 아이디 값 : {}", tripPlaceId.toString())
        log.info("투표 아이디 값 : {}", voteId.toString())
        tripMemberValidator.validMember(tripGroupId, memberId)

        val vote = voteRepository.findById(voteId).orElseThrow(::RuntimeException)
        val tripPlace = tripPlaceRepository.findById(tripPlaceId).orElseThrow(::RuntimeException)
        val voteItem = voteItemRepository.findByVoteIdAndTripPlaceId(voteId, tripPlaceId).orElse(null)

        if (voteItem == null) {
            return try {
                val saved = voteItemRepository.saveAndFlush(VoteItem(vote = vote, tripPlace = tripPlace))
                voteUserService.saveVoteUser(saved, tripGroupId, memberId)
            } catch (e: DataIntegrityViolationException) {
                // 동시성으로 경합에서 진 경우 - 이긴 쪽이 만든 row를 재조회해 정상 흐름으로 이어감
                val winner = voteItemRepository.findByVoteIdAndTripPlaceId(voteId, tripPlaceId)
                    .orElseThrow(::RuntimeException)
                winner.updateTripPlace(tripPlace)
                voteUserService.saveVoteUser(winner, tripGroupId, memberId)
            }
        }
        voteItem.updateTripPlace(tripPlace)
        return voteUserService.saveVoteUser(voteItem, tripGroupId, memberId)
    }

    companion object {
        private val log = LoggerFactory.getLogger(VoteItemService::class.java)
    }
}