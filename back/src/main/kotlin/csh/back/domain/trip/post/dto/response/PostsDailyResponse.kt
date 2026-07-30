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
        @field:Schema(description = "이미지 URL", example = "https://example.com/images/post1.jpg", nullable = true)
        val contentUrl: String?,
        val timelineId: Long?,
        val startTime: LocalDateTime?,
        val endTime: LocalDateTime?,
        val confirmedPlaceName: String?,
        val createdAt: LocalDateTime?
    ) {
        companion object {
            @JvmStatic
            fun from(post: Post): PostSummary {
                val timeline = post.timeline
                return PostSummary(
                    postId = post.id,
                    contentUrl = post.contentUrl,
                    timelineId = timeline?.id,
                    startTime = timeline?.startTime,
                    endTime = timeline?.endTime,
                    confirmedPlaceName = timeline?.tripWishPlace?.name,
                    createdAt = post.createdAt
                )
            }
        }
    }
}
