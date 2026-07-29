package csh.back.domain.vote.user.repository

import csh.back.domain.vote.user.entity.VoteUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.Optional

interface VoteUserRepository : JpaRepository<VoteUser, Long> {

    @Query(
        "SELECT vi.id AS voteItemId, COUNT(vu) AS voteCount " +
            "FROM VoteUser vu JOIN vu.voteItem vi WHERE vi.vote.id = :voteId GROUP BY vi.id",
    )
    fun countGroupByVoteId(voteId: Long): List<VoteCountProjection>

    fun findByVoteItemId(voteItemId: Long): List<VoteUser>

    fun findByVoteIdAndTripMemberId(voteId: Long, tripMemberId: Long): Optional<VoteUser>
}