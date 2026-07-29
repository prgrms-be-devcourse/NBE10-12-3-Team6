package csh.back.domain.trip.post.dto.response;

import csh.back.domain.trip.post.entity.Post;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "게시글 응답 DTO")
public record PostResponse(

        @Schema(description = "게시글 ID", example = "1")
        Long id,

        @Schema(description = "타임라인 ID", example = "1")
        Long timelineId,

        @Schema(description = "게시글 타입", example = "IMAGE")
        String type,

        @Schema(
                description = "이미지 URL",
                example = "https://example.com/images/post1.jpg",
                nullable = true
        )
        String contentUrl

) {
    public static PostResponse from(Post post) {
        Long timeLineId = post.getTimeline() != null ? post.getTimeline().getId() : null;
        return new PostResponse(
                post.getId(),
                timeLineId,
                post.getType(),
                post.getContentUrl()
        );
    }
}