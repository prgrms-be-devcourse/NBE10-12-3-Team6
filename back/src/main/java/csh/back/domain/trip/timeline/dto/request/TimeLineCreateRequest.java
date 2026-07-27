package csh.back.domain.trip.timeline.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

//스웨거 테스트를 위한 스키마
@Schema(description = "타임라인 시간 구간 생성 요청 DTO")
public record TimeLineCreateRequest(

        @Schema(description = "여행 일차 번호", example = "1")
        @NotNull
        @Min(1)
        Integer dayNumber,

        @Schema(description = "타임라인 시작 시간", example = "2026-07-01T09:00:00")
        @NotNull
        LocalDateTime startTime,

        @Schema(description = "타임라인 종료 시간", example = "2026-07-01T10:00:00")
        @NotNull
        LocalDateTime endTime
) {
}