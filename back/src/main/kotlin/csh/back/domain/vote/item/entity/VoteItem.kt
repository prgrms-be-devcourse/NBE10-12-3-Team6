package csh.back.domain.vote.item.entity

import csh.back.domain.trip.place.entity.TripPlace
import csh.back.domain.vote.vote.entity.Vote
import csh.back.global.entity.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "trip_vote_items",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_vote_item", columnNames = ["trip_vote_id", "trip_wish_place_id"]),
    ],
)
class VoteItem(
    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "trip_vote_id", nullable = false)
    val vote: Vote,
    tripPlace: TripPlace,
) : BaseEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "trip_wish_place_id", nullable = false)
    var tripPlace: TripPlace = tripPlace
        protected set

    fun updateTripPlace(tripPlace: TripPlace) {
        this.tripPlace = tripPlace
    }
}