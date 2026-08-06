package csh.back.domain.vote.item.service

import csh.back.domain.trip.place.entity.TripPlace
import csh.back.domain.vote.item.entity.VoteItem
import csh.back.domain.vote.vote.entity.Vote
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class VoteItemInsertExecutor(
    private val em: EntityManager,
) {
    // flush() 실패는 JPA 스펙상 그 물리 트랜잭션을 곧바로 rollback-only로 만들기 때문에, 이 메서드
    // 안에서 예외를 잡고 정상 반환해도 커밋 시점에 UnexpectedRollbackException이 난다.
    // 예외를 그대로 전파시켜 트랜잭션 소유자가 실제로 롤백하게 하고, 호출부에서 잡도록 한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun tryInsert(vote: Vote, tripPlace: TripPlace): VoteItem {
        val voteItem = VoteItem(vote = vote, tripPlace = tripPlace)
        em.persist(voteItem)
        em.flush()
        return voteItem
    }
}