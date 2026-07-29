package csh.back.domain.member.dto.response

import csh.back.domain.member.entity.Member

data class MemberResponseDto(
    val id: Long,
    val email: String,
    val name: String,
) {
    companion object {
        fun from(member: Member) = MemberResponseDto(
            id = member.id!!,
            email = member.email,
            name = member.name,
        )
    }
}