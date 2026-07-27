package csh.back.domain.vote.vote.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "타임라인 확정 장소 반영 요청 DTO")
public record VoteConfirmPlaceRequest(

        @Schema(description = "투표 또는 랜덤 결과로 확정된 후보 장소 ID", example = "1")
        @NotNull
        Long confirmedPlaceId
) {
}