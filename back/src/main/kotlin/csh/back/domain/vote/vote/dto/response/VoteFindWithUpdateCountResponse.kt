package csh.back.domain.vote.vote.dto.response

import csh.back.domain.trip.place.dto.response.TripPlaceFindResponse
import csh.back.domain.vote.vote.entity.Vote

data class VoteFindWithUpdateCountResponse(
    val voteResults: List<VoteFindResponse>,
    val wishPlaceFindResponses: List<TripPlaceFindResponse>,
    val updateCount: Int,
    val voteStatus: String,
    val confirmedPlaceId: Long?,
    val isAnonymous: Boolean,
) {
    companion object {
        @JvmStatic
        fun of(
            voteFindResponses: List<VoteFindResponse>,
            wishPlaceFindResponses: List<TripPlaceFindResponse>,
            updateCount: Int,
            vote: Vote,
        ): VoteFindWithUpdateCountResponse {
            val voteStatus = vote.status
            val timeline = vote.timeline
            val confirmedPlaceId = timeline.tripWishPlace?.id
            return VoteFindWithUpdateCountResponse(
                voteFindResponses,
                wishPlaceFindResponses,
                updateCount,
                voteStatus.nickname,
                confirmedPlaceId,
                vote.isAnonymous,
            )
        }
    }
}
