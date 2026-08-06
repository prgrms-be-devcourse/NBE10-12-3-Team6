package csh.back.domain.trip.post.like.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "게시글 좋아요 상태 응답")
data class PostLikeResponse(
    @field:Schema(
        description = "게시글 ID",
        example = "1"
    )
    val postId: Long,

    @field:Schema(
        description = "현재 사용자의 좋아요 여부",
        example = "true"
    )
    val liked: Boolean,

    @field:Schema(
        description = "게시글의 전체 좋아요 수",
        example = "3"
    )
    val likeCount: Long
)