package csh.back.domain.member.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// verify-code 성공 시 발급받은 verificationToken으로 새 비밀번호를 확정하는 요청
data class ApplyPasswordResetDto(
    @field:NotBlank val verificationToken: String,
    @field:NotBlank @field:Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다") val newPassword: String,
)
