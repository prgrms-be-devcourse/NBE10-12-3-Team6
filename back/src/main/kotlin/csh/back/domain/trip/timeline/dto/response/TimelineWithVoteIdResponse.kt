package csh.back.domain.trip.timeline.dto.response

import csh.back.domain.trip.timeline.entity.Timeline
import java.time.LocalDateTime

data class TimelineWithVoteIdResponse(
    val timelineId: Long,
    val dayNumber: Long,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val confirmedPlaceName: String?,
    val category: String?,
    val voteId: Long?,
) {
    companion object {
        @JvmStatic
        fun of(timeline: Timeline, voteId: Long?): TimelineWithVoteIdResponse {
            val tripWishPlace = timeline.tripWishPlace

            return TimelineWithVoteIdResponse(
                timelineId = requireNotNull(timeline.id),
                dayNumber = timeline.dayNumber,
                startTime = timeline.startTime,
                endTime = timeline.endTime,
                confirmedPlaceName = tripWishPlace?.name,
                category = tripWishPlace?.category,
                voteId = voteId,
            )
        }
    }
}
