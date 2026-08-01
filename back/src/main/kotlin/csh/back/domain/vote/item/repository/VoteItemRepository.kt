package csh.back.domain.vote.item.repository

import csh.back.domain.vote.item.entity.VoteItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.Optional

interface VoteItemRepository : JpaRepository<VoteItem, Long> {

    @Query("SELECT vi FROM VoteItem vi JOIN FETCH vi.tripPlace WHERE vi.vote.id = :voteId")
    fun findAllByVoteIdWithTripPlace(voteId: Long): List<VoteItem>

    fun findByVoteIdAndTripPlaceId(voteId: Long, tripPlaceId: Long): Optional<VoteItem>

    fun existsByTripPlaceId(tripPlaceId: Long): Boolean
}