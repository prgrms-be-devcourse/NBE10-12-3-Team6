package csh.back.domain.trip.post.dto.response

import csh.back.domain.trip.post.entity.Post
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalDateTime

data class PostsDailyResponse(
    val date: LocalDate,
    val posts: List<PostSummary>
) {
    data class PostSummary(
        val postId: Long?,
        @field:Schema(description = "업로드 당시 원본 파일명", example = "IMG_1234.JPG", nullable = true)
        val originalFilename: String? = null,
        @field:Schema(description = "이미지 URL", example = "https://example.com/images/post1.jpg", nullable = true)
        val contentUrl: String?,
        @field:Schema(description = "일반 모드 목록용 WebP 이미지 URL", nullable = true)
        val normalContentUrl: String? = contentUrl,
        @field:Schema(description = "데이터 절약 모드용 WebP 이미지 URL", nullable = true)
        val dataSaverContentUrl: String? = normalContentUrl ?: contentUrl,
        val timelineId: Long?,
        val startTime: LocalDateTime?,
        val endTime: LocalDateTime?,
        val confirmedPlaceName: String?,
        val createdAt: LocalDateTime?,
        @field:Schema(description = "게시글의 전체 좋아요 수", example = "3")
        val likeCount: Long
    ) {
        companion object {
            @JvmStatic
            fun from(post: Post, likeCount: Long = 0L): PostSummary {
                val timeline = post.timeline
                return PostSummary(
                    postId = post.id,
                    originalFilename = post.originalFilename,
                    contentUrl = post.contentUrl,
                    normalContentUrl = post.normalContentUrl ?: post.contentUrl,
                    dataSaverContentUrl = post.dataSaverContentUrl
                        ?: post.normalContentUrl
                        ?: post.contentUrl,
                    timelineId = timeline?.id,
                    startTime = timeline?.startTime,
                    endTime = timeline?.endTime,
                    confirmedPlaceName = timeline?.tripWishPlace?.name,
                    createdAt = post.createdAt,
                    likeCount = likeCount
                )
            }
        }
    }
}
