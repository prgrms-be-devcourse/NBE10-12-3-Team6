package csh.back.domain.vote.vote.dto.response

import csh.back.domain.vote.vote.entity.Vote

data class VoteCreateResponse(
    val voteId: Long?,
) {
    companion object {
        @JvmStatic
        fun from(vote: Vote): VoteCreateResponse = VoteCreateResponse(vote.id)
    }
}
