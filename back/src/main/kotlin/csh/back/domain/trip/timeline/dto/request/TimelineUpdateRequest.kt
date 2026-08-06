package csh.back.domain.trip.timeline.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

@Schema(description = "타임라인 시간 구간 수정 요청 DTO")
data class TimelineUpdateRequest(
    @field:Schema(description = "수정할 타임라인 시작 시간", example = "2026-07-01T09:30:00")
    @field:NotNull
    val startTime: LocalDateTime,

    @field:Schema(description = "수정할 타임라인 종료 시간", example = "2026-07-01T10:30:00")
    @field:NotNull
    val endTime: LocalDateTime,
)
