package csh.back.domain.trip.group.entity

import csh.back.domain.member.entity.Member
import csh.back.domain.trip.group.dto.request.TripGroupModifyRequest
import csh.back.domain.trip.member.entity.TripMember
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
class TripGroup protected constructor() : BaseEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "member_id", nullable = false)
    lateinit var owner: Member
        protected set

    lateinit var name: String
        protected set

    lateinit var region: String
        protected set

    var nights: Int = 0
        protected set

    @field:Column(unique = true)
    lateinit var joinCode: String
        protected set

    lateinit var startDate: LocalDate
        protected set

    lateinit var endDate: LocalDate
        protected set

    @field:ColumnDefault("false")
    var isVote: Boolean = false
        protected set

    private constructor(
        owner: Member,
        name: String,
        region: String,
        nights: Int,
        joinCode: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ) : this() {
        this.owner = owner
        this.name = name
        this.region = region
        this.nights = nights
        this.joinCode = joinCode
        this.startDate = startDate
        this.endDate = endDate
    }

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

    class Builder {
        private var owner: Member? = null
        private var name: String? = null
        private var region: String? = null
        private var nights: Int? = null
        private var joinCode: String? = null
        private var startDate: LocalDate? = null
        private var endDate: LocalDate? = null

        fun owner(owner: Member?) = apply { this.owner = owner }
        fun name(name: String?) = apply { this.name = name }
        fun region(region: String?) = apply { this.region = region }
        fun nights(nights: Int?) = apply { this.nights = nights }
        fun joinCode(joinCode: String?) = apply { this.joinCode = joinCode }
        fun startDate(startDate: LocalDate?) = apply { this.startDate = startDate }
        fun endDate(endDate: LocalDate?) = apply { this.endDate = endDate }

        fun build(): TripGroup = TripGroup(
            owner = requireNotNull(owner),
            name = requireNotNull(name),
            region = requireNotNull(region),
            nights = requireNotNull(nights),
            joinCode = requireNotNull(joinCode),
            startDate = requireNotNull(startDate),
            endDate = requireNotNull(endDate),
        )
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
