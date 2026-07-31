package csh.back.domain.trip.group.settings.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class TripGroupSettingsUpdateRequest(
    @field:NotNull(message = "자유시간 범위는 필수입니다.")
    @field:Min(value = 30, message = "자유시간 범위는 최소 30분입니다.")
    @field:Max(value = 180, message = "자유시간 범위는 최대 3시간입니다.")
    val freeTimeMinutes: Int?,
) {
    fun freeTimeMinutesOrThrow(): Int =
        requireNotNull(freeTimeMinutes) {
            "자유시간 범위는 필수입니다."
        }
}
