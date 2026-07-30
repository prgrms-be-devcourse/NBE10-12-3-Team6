package csh.back.domain.vote.user.service

import csh.back.domain.trip.member.repository.TripMemberRepository
import csh.back.domain.vote.item.entity.VoteItem
import csh.back.domain.vote.user.dto.response.VoteUserSaveResponseDto
import csh.back.domain.vote.user.entity.VoteUser
import csh.back.domain.vote.user.repository.VoteUserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class VoteUserService(
    private val voteUserRepository: VoteUserRepository,
    private val tripMemberRepository: TripMemberRepository,
) {

    @Transactional
    fun saveVoteUser(voteItem: VoteItem, tripGroupId: Long, memberId: Long): VoteUserSaveResponseDto {
        val tripMember = tripMemberRepository.findByMemberIdAndTripGroupId(memberId, tripGroupId)
            .orElseThrow(::RuntimeException)
        val voteUser = voteUserRepository
            .findByVoteIdAndTripMemberId(voteItem.vote.id!!, tripMember.id!!)
            .orElse(null)

        if (voteUser == null) {
            return try {
                val saved = voteUserRepository.saveAndFlush(
                    VoteUser(
                        vote = voteItem.vote,
                        voteItem = voteItem,
                        tripMember = tripMember,
                        updateCount = DEFAULT_UPDATE_COUNT,
                    ),
                )
                VoteUserSaveResponseDto.from(saved)
            } catch (e: DataIntegrityViolationException) {
                // 동시성으로 경합에서 진 경우 - 이긴 쪽이 만든 row를 재조회해 정상 흐름으로 이어감
                val winner = voteUserRepository.findByVoteIdAndTripMemberId(voteItem.vote.id!!, tripMember.id!!)
                    .orElseThrow(::RuntimeException)
                if (winner.updateCount != 2) winner.updateVoteItemAndincreaseUpdateCount(voteItem)
                VoteUserSaveResponseDto.from(winner)
            }
        }
        if (voteUser.updateCount == 2) throw RuntimeException()
        voteUser.updateVoteItemAndincreaseUpdateCount(voteItem)
        return VoteUserSaveResponseDto.from(voteUser)
    }

    companion object {
        private const val DEFAULT_UPDATE_COUNT = 0
    }
}