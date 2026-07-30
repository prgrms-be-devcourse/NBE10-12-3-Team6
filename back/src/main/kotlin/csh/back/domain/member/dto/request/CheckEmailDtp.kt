package csh.back.domain.member.dto.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class CheckEmailDtp (
    @field:NotBlank @field:Email val email: String
)