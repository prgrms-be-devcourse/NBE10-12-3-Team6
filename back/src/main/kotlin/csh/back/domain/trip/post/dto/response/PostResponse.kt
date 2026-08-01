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
    @field:Schema(description = "일반 모드 목록용 WebP 이미지 URL", nullable = true)
    val normalContentUrl: String? = contentUrl,
    @field:Schema(description = "데이터 절약 모드용 WebP 이미지 URL", nullable = true)
    val dataSaverContentUrl: String? = normalContentUrl ?: contentUrl,
    @field:Schema(description = "게시글 내용", example = "부산 여행 시작!")
    val content: String?,
    @field:Schema(description = "게시글의 전체 좋아요 수",example = "3")
    val likeCount: Long
) {
    companion object {
        @JvmStatic
        fun from(post: Post, likeCount: Long = 0L) = PostResponse(
            id = post.id,
            timelineId = post.timeline?.id,
            type = post.type,
            contentUrl = post.contentUrl,
            normalContentUrl = post.normalContentUrl ?: post.contentUrl,
            dataSaverContentUrl = post.dataSaverContentUrl
                ?: post.normalContentUrl
                ?: post.contentUrl,
            content = post.content,
            likeCount = likeCount
        )
    }
}
