package csh.back.domain.trip.group.entity

import csh.back.domain.member.entity.Member
import csh.back.domain.trip.group.dto.request.TripGroupModifyRequest
import csh.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnDefault
import java.time.LocalDate

@Entity
@Table(name = "trip_groups")
class TripGroup(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val owner: Member,

    var name: String,
    var region: String,
    var nights: Int,

    @Column(unique = true)
    val joinCode: String,

    var startDate: LocalDate,
    var endDate: LocalDate,
) : BaseEntity() {

    @ColumnDefault("false")
    var isVote: Boolean = false

    fun modify(request: TripGroupModifyRequest) {
        if (!request.name.isNullOrBlank()) this.name = request.name
        if (!request.region.isNullOrBlank()) this.region = request.region
        if (!request.startDate.isNullOrBlank()) {
            this.startDate = LocalDate.parse(request.startDate)
            this.endDate = this.startDate.plusDays(this.nights.toLong())
        }
        if (request.nights != null) {
            this.nights = request.nights
            this.endDate = this.startDate.plusDays(request.nights.toLong())
        }
    }
}
