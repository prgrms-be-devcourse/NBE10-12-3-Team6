package csh.back.domain.member.controller

import csh.back.domain.member.dto.request.ApplyPasswordResetDto
import csh.back.domain.member.dto.request.VerifyResetCodeDto
import csh.back.domain.member.dto.response.VerifyResetCodeResponseDto
import csh.back.domain.member.service.PasswordResetService
import csh.back.global.annotation.ApiV1
import csh.back.global.dto.ResponseData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// 비로그인 상태 비밀번호 재설정 컨트롤러 (recovery code 기반).
// @ApiV1: 프로젝트 커스텀 어노테이션 — /api/v1 prefix 자동 부여
// 최종 경로: /api/v1/auth/password-reset/{verify-code, apply}
// SecurityConfig의 permitAll에 등록 필요 (비번 잊은 미인증 사용자가 접근).
@ApiV1
@RestController
@RequestMapping("/auth/password-reset")
@Tag(name = "비밀번호 재설정", description = "Recovery code 기반 비밀번호 재설정 (비로그인 상태)")
class PasswordResetController(
    private val passwordResetService: PasswordResetService,
) {

    // 1단계: 이메일 + 회원가입 시 발급받은 recovery code 검증 → verificationToken 발급
    // @Valid: DTO의 @NotBlank/@Email 검증 트리거. 실패 시 MethodArgumentNotValidException → 400
    @Operation(summary = "이메일 + 코드 검증 후 verificationToken 발급")
    @PostMapping("/verify-code")
    fun verifyCode(@RequestBody @Valid dto: VerifyResetCodeDto): ResponseData<VerifyResetCodeResponseDto> {
        val token = passwordResetService.verifyCode(dto.email, dto.code)
        return ResponseData(200, VerifyResetCodeResponseDto(verificationToken = token))
    }

    // 2단계: verify-code에서 받은 verificationToken으로 새 비밀번호 확정
    // 성공 시 락 해제 + 모든 기기 로그아웃 (탈취 시나리오 방어)
    @Operation(summary = "verificationToken으로 새 비밀번호 저장")
    @PostMapping("/apply")
    fun apply(@RequestBody @Valid dto: ApplyPasswordResetDto): ResponseData<Void?> {
        passwordResetService.apply(dto.verificationToken, dto.newPassword)
        return ResponseData(200, null)
    }
}
