package csh.back.domain.member.dto.response

import csh.back.domain.member.entity.Member

data class AuthFilterDto(
    val id: Long,
    val email: String,
) {
    companion object {
        fun from(member: Member) = AuthFilterDto(
            id = member.id!!,
            email = member.email,
        )
    }
}