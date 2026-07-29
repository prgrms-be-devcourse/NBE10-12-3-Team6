package csh.back.domain.vote.vote.entity

import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.timeline.entity.Timeline
import csh.back.domain.vote.vote.enums.VoteStatus
import csh.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "trip_votes")
class Vote(
    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "trip_group_id", nullable = false)
    val tripGroup: TripGroup,
    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "trip_timeline_id", nullable = false)
    val timeline: Timeline,
    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "trip_member_id", nullable = false)
    val tripMember: TripMember,
    @field:Column(nullable = false)
    val expireTime: LocalDateTime,
) : BaseEntity() {

    @field:Enumerated(STRING)
    var status: VoteStatus = VoteStatus.PENDING
        protected set

    fun updateStatus(status: VoteStatus) {
        this.status = status
    }
}
