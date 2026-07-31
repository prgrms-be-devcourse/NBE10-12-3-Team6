package csh.back.domain.trip.group.settings.entity

import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.settings.exception.InvalidFreeTimeMinutesException
import csh.back.global.entity.BaseEntity
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapKeyColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnDefault

@Entity
@Table(name = "trip_group_settings")
class TripGroupSettings(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_group_id", nullable = false, unique = true)
    val tripGroup: TripGroup,

    @ColumnDefault("true")
    @Column(nullable = false)
    var isAnonymousVote: Boolean = true,

    @ColumnDefault("60")
    @Column(nullable = false)
    var freeTimeMinutes: Int = 60,

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "trip_day_free_time_settings",
        joinColumns = [JoinColumn(name = "trip_group_settings_id")],
    )
    @MapKeyColumn(name = "day_number")
    @Column(name = "free_time_minutes", nullable = false)
    val freeTimeMinutesByDay: MutableMap<Int, Int> = mutableMapOf(),

    @ColumnDefault("0")
    @Column(nullable = false)
    var lastFreeTimeGeneratedDay: Long = 0L,
) : BaseEntity() {

    fun freeTimeMinutesFor(dayNumber: Long): Int =
        freeTimeMinutesByDay[dayNumber.toInt()] ?: freeTimeMinutes

    fun updateFreeTimeMinutes(
        dayNumber: Int,
        freeTimeMinutes: Int,
    ) {
        validateFreeTimeMinutes(freeTimeMinutes)
        freeTimeMinutesByDay[dayNumber] = freeTimeMinutes
    }

    fun updateAllFreeTimeMinutes(
        totalDays: Int,
        freeTimeMinutes: Int,
    ) {
        validateFreeTimeMinutes(freeTimeMinutes)
        this.freeTimeMinutes = freeTimeMinutes
        freeTimeMinutesByDay.clear()
        (1..totalDays).forEach { dayNumber ->
            freeTimeMinutesByDay[dayNumber] = freeTimeMinutes
        }
    }

    fun markFreeTimeGenerated(dayNumber: Long) {
        if (dayNumber > lastFreeTimeGeneratedDay) {
            lastFreeTimeGeneratedDay = dayNumber
        }
    }

    companion object {
        const val FREE_TIME_UNIT_MINUTES = 30
        const val MINIMUM_FREE_TIME_MINUTES = 30
        const val MAXIMUM_FREE_TIME_MINUTES = 3 * 60

        fun validateFreeTimeMinutes(freeTimeMinutes: Int) {
            if (
                freeTimeMinutes !in MINIMUM_FREE_TIME_MINUTES..MAXIMUM_FREE_TIME_MINUTES ||
                freeTimeMinutes % FREE_TIME_UNIT_MINUTES != 0
            ) {
                throw InvalidFreeTimeMinutesException(
                    "자유시간은 30분 단위로 30분 이상 3시간 이하이어야 합니다.",
                )
            }
        }
    }
}
