package csh.back.domain.member.dto.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class LoginRequestDto(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank val password: String,
    val joinCode: String?, // 여행 초대 코드 (없으면 null) — 로그인과 동시에 여행 참여 처리
)