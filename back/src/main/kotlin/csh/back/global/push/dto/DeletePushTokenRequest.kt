package csh.back.global.push.dto

import jakarta.validation.constraints.NotBlank

data class DeletePushTokenRequest(
    @field:NotBlank(
        message = "FCM 토큰은 필수입니다."
    )
    val token: String
)