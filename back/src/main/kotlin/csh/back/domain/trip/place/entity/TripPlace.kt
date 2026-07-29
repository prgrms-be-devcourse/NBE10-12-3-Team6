package csh.back.domain.trip.place.entity

import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.member.entity.TripMember
import csh.back.global.entity.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "trip_wish_places",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_wish_place", columnNames = ["kakao_place_id", "trip_group_id"]),
    ],
)
class TripPlace(
    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "trip_group_id", nullable = false)
    val tripGroup: TripGroup,
    val name: String,
    val category: String,
    val address: String,
    val kakaoPlaceId: String,
    val kakaoMapUrl: String,
    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "trip_member_id", nullable = false)
    val createdBy: TripMember,
) : BaseEntity()