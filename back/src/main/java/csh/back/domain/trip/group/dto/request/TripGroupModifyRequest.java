package csh.back.domain.trip.group.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

//스웨거 테스트를 위한 스키마
@Schema(description = "여행 모임 수정 요청 DTO")
public record TripGroupModifyRequest(
		@Schema(description = "여행 모임 명", example = "6팀의 여행 계획")
		@NotBlank
		String name,

		@Schema(description = "여행 갈 장소", example = "강릉")
		@NotBlank
		String region,

		@Schema(description = "여행 시작 날짜", example = "2026-07-01")
		@NotBlank
		String startDate,

		@Schema(description = "이용 일수", example = "2")
		@NotNull
		@Min(0)
		Integer nights
) {
}
