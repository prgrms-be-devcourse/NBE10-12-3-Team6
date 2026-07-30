package csh.back.domain.trip.group.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

@Schema(description = "여행 모임 생성 요청 DTO")
data class TripGroupRequest(
    @field:Schema(description = "여행 모임 명", example = "6팀의 여행 계획")
    @field:NotBlank
    val name: String,

    @field:Schema(description = "여행 갈 장소", example = "강릉")
    @field:NotBlank
    val region: String,

    @field:Schema(description = "여행 시작 날짜", example = "2026-07-01")
    @field:NotBlank
    val startDate: String,

    @field:Schema(description = "이용 일수", example = "2")
    @field:NotNull(message = "nights: must not be null")
    @field:Min(0)
    val nights: Int? = null,
) {
    fun nightsOrThrow(): Int = requireNotNull(nights) { "nights must not be null" }
}
