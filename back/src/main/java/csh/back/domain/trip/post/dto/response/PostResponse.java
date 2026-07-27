package csh.back.domain.trip.post.dto.response;

import csh.back.domain.trip.post.entity.Post;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "게시글 응답 DTO")
public record PostResponse(

        @Schema(description = "게시글 ID", example = "1")
        Long id,

        @Schema(description = "타임라인 ID", example = "1")
        Long timelineId,

        @Schema(description = "게시글 내용", example = "부산 여행 시작!")
        String content,

        @Schema(description = "게시글 위치", example = "부산 광안리")
        String location,

        @Schema(description = "이미지 여부", example = "true")
        Boolean isImg,

        @Schema(
                description = "이미지 URL",
                example = "https://example.com/images/post1.jpg",
                nullable = true
        )
        String contentUrl

) {

    public static PostResponse from(Post post) {
        Long timeLineId = post.getTimeLine() != null ? post.getTimeLine().getId() : null;
        return new PostResponse(
                post.getId(),
                timeLineId,
                post.getContent(),
                post.getLocation(),
                post.getIsImg(),
                post.getContentUrl()
        );
    }
}