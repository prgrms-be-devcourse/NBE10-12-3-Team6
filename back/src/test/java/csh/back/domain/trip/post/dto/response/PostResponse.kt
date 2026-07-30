package csh.back.domain.trip.post.dto.response

import csh.back.domain.trip.post.entity.Post
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "게시글 응답 DTO")
data class PostResponse(
    @field:Schema(description = "게시글 ID", example = "1")
    val id: Long?,
    @field:Schema(description = "타임라인 ID", example = "1")
    val timelineId: Long?,
    @field:Schema(description = "게시글 타입", example = "IMAGE")
    val type: String?,
    @field:Schema(description = "이미지 URL", example = "https://example.com/images/post1.jpg", nullable = true)
    val contentUrl: String?,
    @field:Schema(description = "게시글 내용", example = "부산 여행 시작!")
    val content: String?
) {
    companion object {
        @JvmStatic
        fun from(post: Post) = PostResponse(
            id = post.id,
            timelineId = post.timeline?.id,
            type = post.type,
            contentUrl = post.contentUrl,
            content = post.content
        )
    }
}
