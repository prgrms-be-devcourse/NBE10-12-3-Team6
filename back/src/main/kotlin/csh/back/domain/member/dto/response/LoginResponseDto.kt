package csh.back.domain.member.dto.response

import csh.back.domain.member.entity.Member

data class LoginResponseDto(
    val id: Long,
    val email: String,
    val name: String,
) {
    companion object {
        fun from(member: Member) = LoginResponseDto(
            id = member.id!!,
            email = member.email,
            name = member.name,
        )
    }
}