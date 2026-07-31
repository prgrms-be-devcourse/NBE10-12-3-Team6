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
    val isFreeTime: Boolean,
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
                confirmedPlaceName = if (timeline.isFreeTime) FREE_TIME_NAME else tripWishPlace?.name,
                category = tripWishPlace?.category,
                voteId = if (timeline.isFreeTime) null else voteId,
                isFreeTime = timeline.isFreeTime,
            )
        }

        private const val FREE_TIME_NAME = "자유시간"
    }
}
