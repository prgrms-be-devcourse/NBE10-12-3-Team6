package csh.back.domain.trip.post.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

public record CreatePostRequest(
        Long timeLineId,

        @Schema(description = "게시글 내용", example = "부산 여행 시작!")
        String content,

        @Schema(description = "위치", example = "부산 광안리")
        String location
) {
}