package csh.back.domain.vote.user.entity

import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.vote.item.entity.VoteItem
import csh.back.domain.vote.vote.entity.Vote
import csh.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "trip_vote_users",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_vote_user", columnNames = ["trip_vote_id", "trip_member_id"]),
    ],
)
class VoteUser(
    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "trip_vote_id", nullable = false)
    val vote: Vote,
    voteItem: VoteItem,
    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "trip_member_id", nullable = false)
    val tripMember: TripMember,
    updateCount: Int,
) : BaseEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "trip_vote_item_id", nullable = false)
    var voteItem: VoteItem = voteItem
        protected set

    @field:Column(nullable = false)
    var updateCount: Int = updateCount
        protected set

    fun updateVoteItemAndincreaseUpdateCount(voteItem: VoteItem) {
        this.voteItem = voteItem
        this.updateCount++
    }
}