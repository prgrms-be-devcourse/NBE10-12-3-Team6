package csh.back.domain.member.dto.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

// @field: 접두사: Kotlin data class에서 어노테이션이 생성자 파라미터가 아닌 필드에 적용되도록 지정
data class MemberRequestDto(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank val password: String,
    @field:NotBlank val name: String,
)