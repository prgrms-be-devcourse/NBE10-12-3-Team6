package csh.back.domain.trip.post.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record UpdatePostRequest(

        @Schema(description = "게시글 내용", example = "내용 수정")
        String content,

        @Schema(description = "위치", example = "해운대")
        String location
) {
}