package csh.back.domain.member.dto.request

import jakarta.validation.constraints.NotBlank

// verify-code 성공 시 발급받은 verificationToken으로 새 비밀번호를 확정하는 요청
data class ApplyPasswordResetDto(
    @field:NotBlank val verificationToken: String,
    @field:NotBlank val newPassword: String,
)
