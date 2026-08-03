package csh.back.domain.member.dto.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

// 이메일 + 회원가입 시 발급받은 recovery code 조합 검증 요청
// @field: 접두사 — Kotlin data class에서 실제 필드에 검증 어노테이션 적용 (@Valid가 트리거)
data class VerifyResetCodeDto(
    @field:NotBlank @field:Email val email: String,
    // 형식/길이 검증은 도메인/서비스 계층 — DTO는 blank만 방어
    @field:NotBlank val code: String,
)
