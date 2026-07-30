package csh.back.domain.trip.post.dto.response

import java.time.LocalDateTime

data class PostTimelineResponse(
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val timelineId: Long?,
    val confirmedPlaceName: String?,
    val isTaken: Boolean
) {
    companion object {
        @JvmStatic
        fun of(
            startTime: LocalDateTime,
            endTime: LocalDateTime,
            timelineId: Long?,
            confirmedPlaceName: String?,
            isTaken: Boolean
        ) = PostTimelineResponse(startTime, endTime, timelineId, confirmedPlaceName, isTaken)
    }
}
