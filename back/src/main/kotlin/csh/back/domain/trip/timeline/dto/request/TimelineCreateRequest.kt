package csh.back.domain.trip.timeline.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

@Schema(description = "타임라인 시간 구간 생성 요청 DTO")
data class TimelineCreateRequest(
    @field:Schema(description = "여행 일차 번호", example = "1")
    @field:NotNull
    @field:Min(1)
    val dayNumber: Long,

    @field:Schema(description = "타임라인 시작 시간", example = "2026-07-01T09:00:00")
    @field:NotNull
    val startTime: LocalDateTime,

    @field:Schema(description = "타임라인 종료 시간", example = "2026-07-01T10:00:00")
    @field:NotNull
    val endTime: LocalDateTime,
)
