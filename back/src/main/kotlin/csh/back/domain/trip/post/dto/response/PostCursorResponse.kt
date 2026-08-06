package csh.back.domain.trip.post.dto.response

import io.swagger.v3.oas.annotations.media.Schema

data class PostCursorResponse(
    val groups: List<PostsDailyResponse>,
    @field:Schema(description = "해당 일차의 다음 5개 조회에 사용할 커서", nullable = true)
    val nextCursor: String?,
    val hasNext: Boolean
)
