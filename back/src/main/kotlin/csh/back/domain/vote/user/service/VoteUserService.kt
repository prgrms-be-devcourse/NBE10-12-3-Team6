package csh.back.domain.vote.user.service

import csh.back.domain.trip.member.repository.TripMemberRepository
import csh.back.domain.vote.item.entity.VoteItem
import csh.back.domain.vote.user.dto.response.VoteUserSaveResponseDto
import csh.back.domain.vote.user.repository.VoteUserRepository
import jakarta.persistence.PersistenceException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class VoteUserService(
    private val voteUserRepository: VoteUserRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val voteUserInsertExecutor: VoteUserInsertExecutor,
) {

    @Transactional
    fun saveVoteUser(voteItem: VoteItem, tripGroupId: Long, memberId: Long): VoteUserSaveResponseDto {
        val tripMember = tripMemberRepository.findByMemberIdAndTripGroupId(memberId, tripGroupId)
            .orElseThrow(::RuntimeException)
        val voteUser = voteUserRepository
            .findByVoteIdAndTripMemberId(voteItem.vote.id!!, tripMember.id!!)
            .orElse(null)

        if (voteUser == null) {
            val saved = try {
                voteUserInsertExecutor.tryInsert(voteItem.vote, voteItem, tripMember)
            } catch (e: PersistenceException) {
                null
            }
            if (saved != null) {
                return VoteUserSaveResponseDto.from(saved)
            }
            // 동시성으로 경합에서 진 경우 - 이긴 쪽이 만든 row를 재조회해 정상 흐름으로 이어감
            val winner = voteUserRepository.findByVoteIdAndTripMemberId(voteItem.vote.id!!, tripMember.id!!)
                .orElseThrow(::RuntimeException)
            if (winner.updateCount != 2) winner.updateVoteItemAndincreaseUpdateCount(voteItem)
            return VoteUserSaveResponseDto.from(winner)
        }
        if (voteUser.updateCount == 2) throw RuntimeException()
        voteUser.updateVoteItemAndincreaseUpdateCount(voteItem)
        return VoteUserSaveResponseDto.from(voteUser)
    }
}