package csh.back.domain.vote.user.service

import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.vote.item.entity.VoteItem
import csh.back.domain.vote.user.entity.VoteUser
import csh.back.domain.vote.vote.entity.Vote
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class VoteUserInsertExecutor(
    private val em: EntityManager,
) {
    // flush() 실패는 JPA 스펙상 그 물리 트랜잭션을 곧바로 rollback-only로 만들기 때문에, 이 메서드
    // 안에서 예외를 잡고 정상 반환해도 커밋 시점에 UnexpectedRollbackException이 난다.
    // 예외를 그대로 전파시켜 트랜잭션 소유자가 실제로 롤백하게 하고, 호출부에서 잡도록 한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun tryInsert(vote: Vote, voteItem: VoteItem, tripMember: TripMember): VoteUser {
        val voteUser = VoteUser(vote, voteItem, tripMember, 0)
        em.persist(voteUser)
        em.flush()
        return voteUser
    }
}