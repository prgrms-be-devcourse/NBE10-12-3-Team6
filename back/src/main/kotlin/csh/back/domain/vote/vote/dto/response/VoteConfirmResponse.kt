package csh.back.domain.vote.vote.dto.response

data class VoteConfirmResponse(
    val voteStatus: String,
    val confirmedPlaceId: Long,
    val isTie: Boolean,
) {
    companion object {
        @JvmStatic
        fun of(voteStatus: String, confirmedPlaceId: Long, isTie: Boolean): VoteConfirmResponse =
            VoteConfirmResponse(voteStatus, confirmedPlaceId, isTie)
    }
}
