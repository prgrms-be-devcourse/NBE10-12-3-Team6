package csh.back.domain.vote.vote.dto.response

import csh.back.domain.vote.user.entity.VoteUser

data class VoteFindUserResponse(
    val name: String,
) {
    companion object {
        @JvmStatic
        fun from(voteUser: VoteUser): VoteFindUserResponse =
            VoteFindUserResponse(voteUser.tripMember.member.name)
    }
}
