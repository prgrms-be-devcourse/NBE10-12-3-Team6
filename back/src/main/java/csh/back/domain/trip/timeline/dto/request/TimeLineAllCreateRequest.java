package csh.back.domain.trip.timeline.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

//스웨거 테스트를 위한 스키마
@Schema(description = "타임라인 시간 구간 일괄 생성 요청 DTO")
public record TimeLineAllCreateRequest(
        @Schema(description = "여행 일차 번호", example = "1")
        @NotNull
        @Min(1)
        Integer dayNumber,

        @Schema(description = "생성할 타임라인 시간 구간 목록")
        @NotEmpty
        List<@Valid TimeLineCreateRequest> timeLines
) {
}