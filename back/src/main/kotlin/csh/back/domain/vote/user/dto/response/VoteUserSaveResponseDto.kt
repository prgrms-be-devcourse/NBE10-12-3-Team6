package csh.back.domain.vote.user.dto.response

import csh.back.domain.vote.user.entity.VoteUser

data class VoteUserSaveResponseDto(
    val memberName: String,
    val place: String,
    val updateCount: Int,
) {
    companion object {
        @JvmStatic
        fun from(voteUser: VoteUser): VoteUserSaveResponseDto = VoteUserSaveResponseDto(
            memberName = voteUser.tripMember.member.name,
            place = voteUser.voteItem.tripPlace.name,
            updateCount = voteUser.updateCount,
        )
    }
}