package csh.back.domain.vote.vote.dto.response

data class VoteFindResponse(
    val tripPlaceId: Long?,
    val place: String,
    val count: Long,
    val isVoted: Boolean,
    val voters: List<VoterResponse>,
) {
    companion object {
        @JvmStatic
        fun of(tripPlaceId: Long?, place: String, count: Long, isVoted: Boolean, voters: List<VoterResponse>): VoteFindResponse =
            VoteFindResponse(tripPlaceId, place, count, isVoted, voters)
    }
}
