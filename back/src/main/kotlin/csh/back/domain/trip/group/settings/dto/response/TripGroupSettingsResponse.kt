package csh.back.domain.trip.group.settings.dto.response

import csh.back.domain.trip.group.settings.entity.TripGroupSettings

data class TripGroupSettingsResponse(
    val tripGroupId: Long,
    val isAnonymousVote: Boolean,
    val days: List<TripDayFreeTimeSettingsResponse>,
    val editable: Boolean,
) {
    companion object {
        fun from(
            settings: TripGroupSettings,
            editable: Boolean,
        ): TripGroupSettingsResponse =
            TripGroupSettingsResponse(
                tripGroupId = requireNotNull(settings.tripGroup.id),
                isAnonymousVote = settings.isAnonymousVote,
                days = (1..(settings.tripGroup.nights + 1)).map { dayNumber ->
                    TripDayFreeTimeSettingsResponse(
                        dayNumber = dayNumber,
                        freeTimeMinutes = settings.freeTimeMinutesFor(dayNumber.toLong()),
                    )
                },
                editable = editable,
            )
    }
}

data class TripDayFreeTimeSettingsResponse(
    val dayNumber: Int,
    val freeTimeMinutes: Int,
)
