package csh.back.domain.trip.timeline.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

@Schema(description = "타임라인 시간 구간 일괄 생성 요청 DTO")
data class TimelineAllCreateRequest(
    @field:Schema(description = "여행 일차 번호", example = "1")
    @field:NotNull
    @field:Min(1)
    val dayNumber: Long,

    @field:Schema(description = "생성할 타임라인 시간 구간 목록")
    @field:NotEmpty
    @field:Valid
    val timelines: List<TimelineCreateRequest>,
)
