package csh.back.domain.trip.post.dto.request

import io.swagger.v3.oas.annotations.media.Schema

data class UpdatePostRequest(
    @field:Schema(description = "게시글 내용", example = "내용 수정")
    val content: String?
)
