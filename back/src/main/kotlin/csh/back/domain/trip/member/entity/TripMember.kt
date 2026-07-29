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
class TripMember protected constructor() : BaseEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "member_id", nullable = false)
    lateinit var member: Member
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "trip_group_id", nullable = false)
    lateinit var tripGroup: TripGroup
        protected set

    var isAdmin: Boolean = false
        protected set

    private constructor(
        member: Member,
        tripGroup: TripGroup,
        isAdmin: Boolean,
    ) : this() {
        this.member = member
        this.tripGroup = tripGroup
        this.isAdmin = isAdmin
    }

    class Builder {
        private var member: Member? = null
        private var tripGroup: TripGroup? = null
        private var isAdmin: Boolean = false

        fun member(member: Member?) = apply { this.member = member }
        fun tripGroup(tripGroup: TripGroup?) = apply { this.tripGroup = tripGroup }
        fun isAdmin(isAdmin: Boolean) = apply { this.isAdmin = isAdmin }

        fun build(): TripMember = TripMember(
            member = requireNotNull(member),
            tripGroup = requireNotNull(tripGroup),
            isAdmin = isAdmin,
        )
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
