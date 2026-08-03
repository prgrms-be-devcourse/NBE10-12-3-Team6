package csh.back.global.push.dto

import csh.back.global.push.entity.DevicePlatform
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class RegisterPushTokenRequest(
    @field:NotBlank(
        message = "FCM 토큰은 필수입니다."
    )
    val token: String,

    @field:NotNull(
        message = "기기 플랫폼은 필수입니다."
    )
    val platform: DevicePlatform
)