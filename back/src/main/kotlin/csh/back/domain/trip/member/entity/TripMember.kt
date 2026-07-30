package csh.back.domain.trip.member.entity

import csh.back.domain.member.entity.Member
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.global.entity.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "trip_members",
    uniqueConstraints = [UniqueConstraint(name = "uk_trip_member", columnNames = ["trip_group_id", "member_id"])],
)
class TripMember(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_group_id", nullable = false)
    val tripGroup: TripGroup,

    val isAdmin: Boolean,
) : BaseEntity()
