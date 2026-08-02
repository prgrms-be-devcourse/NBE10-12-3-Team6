package csh.back.global.push.controller

import csh.back.domain.member.dto.response.AuthFilterDto
import csh.back.global.annotation.ApiV1
import csh.back.global.dto.ResponseData
import csh.back.global.push.dto.DeletePushTokenRequest
import csh.back.global.push.dto.RegisterPushTokenRequest
import csh.back.global.push.service.PushTokenService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@ApiV1
@RestController
@RequestMapping("/push-tokens")
@Tag(
    name = "푸시 토큰",
    description = "FCM 기기 토큰 등록 및 비활성화 API"
)
class PushTokenV1Controller(
    private val pushTokenService: PushTokenService
) {

    @PostMapping
    @Operation(
        summary = "FCM 기기 토큰 등록",
        description = "현재 로그인한 회원에게 FCM 기기 토큰을 연결합니다."
    )
    fun register(
        @AuthenticationPrincipal member: AuthFilterDto,
        @Valid
        @RequestBody request: RegisterPushTokenRequest
    ): ResponseData<Unit> {
        pushTokenService.register(
            memberId = member.id,
            token = request.token,
            platform = request.platform
        )

        return ResponseData(
            statusCode = 200,
            data = Unit
        )
    }

    @DeleteMapping
    @Operation(
        summary = "FCM 기기 토큰 비활성화",
        description = "현재 로그인한 회원의 FCM 기기 토큰을 비활성화합니다."
    )
    fun unregister(
        @AuthenticationPrincipal member: AuthFilterDto,
        @Valid
        @RequestBody request: DeletePushTokenRequest
    ): ResponseData<Unit> {
        pushTokenService.unregister(
            memberId = member.id,
            token = request.token
        )

        return ResponseData(
            statusCode = 200,
            data = Unit
        )
    }
}