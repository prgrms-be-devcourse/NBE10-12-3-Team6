package csh.back.domain.vote.vote.repository

import csh.back.domain.trip.timeline.entity.Timeline
import csh.back.domain.vote.vote.entity.Vote
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.Optional

interface VoteRepository : JpaRepository<Vote, Long> {

    @Query("SELECT v FROM Vote v JOIN FETCH v.timeline tl WHERE tl.tripGroup.id = :tripGroupId")
    fun findVotesWithTimelineByTripGroupId(tripGroupId: Long): List<Vote>

    @Query("SELECT v.timeline.id AS timelineId, v.id AS voteId FROM Vote v WHERE v.timeline.id IN :timelineIds")
    fun findVoteIdsByTimelineIds(timelineIds: List<Long>): List<VoteTimelineIdProjection>

    @Query("SELECT v.timeline FROM Vote v WHERE v.id = :voteId")
    fun findTimelineByVoteId(voteId: Long): Optional<Timeline>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vote v WHERE v.id = :voteId")
    fun findByIdWithLock(voteId: Long): Optional<Vote>
}
