package csh.back.domain.trip.post.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record CreatePostRequest(
        Long timeLineId,

        @Schema(description = "게시글 내용", example = "부산 여행 시작!")
        String content
) {
}