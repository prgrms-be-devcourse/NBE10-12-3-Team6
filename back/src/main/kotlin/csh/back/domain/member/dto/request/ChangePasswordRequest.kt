package csh.back.domain.member.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ChangePasswordRequest(
    @field:NotBlank val currentPassword: String,
    @field:NotBlank @field:Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다") val newPassword: String,
)