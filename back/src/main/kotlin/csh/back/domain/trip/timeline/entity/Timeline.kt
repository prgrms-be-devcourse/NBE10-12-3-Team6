package csh.back.domain.trip.timeline.entity

import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.place.entity.TripPlace
import csh.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnDefault
import java.time.LocalDateTime

@Entity
@Table(name = "trip_timelines")
class Timeline protected constructor() : BaseEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "trip_group_id", nullable = false)
    lateinit var tripGroup: TripGroup
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "trip_wish_place_id")
    var tripWishPlace: TripPlace? = null
        protected set

    var dayNumber: Long = 0L
        protected set

    lateinit var startTime: LocalDateTime
        protected set

    lateinit var endTime: LocalDateTime
        protected set

    @field:ColumnDefault("false")
    @field:Column(nullable = false)
    var isFreeTime: Boolean = false
        protected set

    private constructor(
        tripGroup: TripGroup?,
        dayNumber: Long?,
        startTime: LocalDateTime?,
        endTime: LocalDateTime?,
        isFreeTime: Boolean,
    ) : this() {
        validateTimeline(tripGroup, dayNumber, startTime, endTime)

        this.tripGroup = requireNotNull(tripGroup)
        this.dayNumber = requireNotNull(dayNumber)
        this.startTime = requireNotNull(startTime)
        this.endTime = requireNotNull(endTime)
        this.isFreeTime = isFreeTime
    }

    fun updateTimeRange(startTime: LocalDateTime?, endTime: LocalDateTime?) {
        validateTimeRange(startTime, endTime)
        this.startTime = requireNotNull(startTime)
        this.endTime = requireNotNull(endTime)
    }

    fun updateTripWishPlace(tripWishPlace: TripPlace?) {
        this.tripWishPlace = requireNotNull(tripWishPlace) {
            "확정할 장소가 없습니다."
        }
    }

    private fun validateTimeline(
        tripGroup: TripGroup?,
        dayNumber: Long?,
        startTime: LocalDateTime?,
        endTime: LocalDateTime?,
    ) {
        requireNotNull(tripGroup) { "여행 모임은 필수입니다." }
        validateDayNumber(dayNumber)
        validateTimeRange(startTime, endTime)
    }

    private fun validateDayNumber(dayNumber: Long?) {
        requireNotNull(dayNumber) { "일차는 필수입니다." }
        require(dayNumber >= MINIMUM_DAY) {
            "일차는 $MINIMUM_DAY 이상이어야 합니다."
        }
    }

    private fun validateTimeRange(startTime: LocalDateTime?, endTime: LocalDateTime?) {
        require(startTime != null && endTime != null) {
            "시작 시간과 종료 시간은 필수입니다."
        }
        require(endTime.isAfter(startTime)) {
            "종료 시간은 시작 시간보다 늦어야 합니다."
        }
    }

    class Builder {
        private var tripGroup: TripGroup? = null
        private var dayNumber: Long? = null
        private var startTime: LocalDateTime? = null
        private var endTime: LocalDateTime? = null

        fun tripGroup(tripGroup: TripGroup?) = apply {
            this.tripGroup = tripGroup
        }

        fun dayNumber(dayNumber: Long?) = apply {
            this.dayNumber = dayNumber
        }

        fun startTime(startTime: LocalDateTime?) = apply {
            this.startTime = startTime
        }

        fun endTime(endTime: LocalDateTime?) = apply {
            this.endTime = endTime
        }

        fun build(): Timeline = Timeline(
            tripGroup = tripGroup,
            dayNumber = dayNumber,
            startTime = startTime,
            endTime = endTime,
            isFreeTime = false,
        )
    }

    companion object {
        private const val MINIMUM_DAY = 1L

        @JvmStatic
        fun create(
            tripGroup: TripGroup,
            dayNumber: Long,
            startTime: LocalDateTime,
            endTime: LocalDateTime,
        ): Timeline = Timeline(
            tripGroup = tripGroup,
            dayNumber = dayNumber,
            startTime = startTime,
            endTime = endTime,
            isFreeTime = false,
        )

        @JvmStatic
        fun createFreeTime(
            tripGroup: TripGroup,
            dayNumber: Long,
            startTime: LocalDateTime,
            endTime: LocalDateTime,
        ): Timeline = Timeline(
            tripGroup = tripGroup,
            dayNumber = dayNumber,
            startTime = startTime,
            endTime = endTime,
            isFreeTime = true,
        )

        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
