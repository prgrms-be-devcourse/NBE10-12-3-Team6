package csh.back.domain.vote.vote.dto.response

import csh.back.domain.trip.timeline.entity.Timeline
import csh.back.domain.vote.vote.entity.Vote
import java.time.LocalDateTime

data class VoteWithTimelineResponse(
    val voteId: Long?,
    val timelineId: Long?,
    val startTime: LocalDateTime,
    val confirmedPlaceName: String,
    val voteStatus: String,
    val isAnonymous: Boolean,
) {
    companion object {
        @JvmStatic
        fun of(timeline: Timeline, vote: Vote?): VoteWithTimelineResponse {
            val tripPlace = timeline.tripWishPlace
            val confirmedPlaceName = tripPlace?.name ?: ""
            return VoteWithTimelineResponse(
                vote!!.id,
                timeline.id,
                timeline.startTime,
                confirmedPlaceName,
                vote.status.nickname,
                vote.isAnonymous,
            )
        }
    }
}
