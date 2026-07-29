package csh.back.domain.vote.vote.dto.response

data class VoteFindResponse(
    val tripPlaceId: Long?,
    val place: String,
    val count: Long,
    val isVoted: Boolean,
) {
    companion object {
        @JvmStatic
        fun of(tripPlaceId: Long?, place: String, count: Long, isVoted: Boolean): VoteFindResponse =
            VoteFindResponse(tripPlaceId, place, count, isVoted)
    }
}
