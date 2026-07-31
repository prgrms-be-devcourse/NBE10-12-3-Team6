package csh.back.domain.trip.timeline.dto.response

import csh.back.domain.trip.timeline.entity.Timeline
import java.time.LocalDateTime

data class TimelineResponse(
    val timelineId: Long,
    val dayNumber: Long,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val isFreeTime: Boolean,
) {
    companion object {
        @JvmStatic
        fun from(timeline: Timeline): TimelineResponse = TimelineResponse(
            timelineId = requireNotNull(timeline.id),
            dayNumber = timeline.dayNumber,
            startTime = timeline.startTime,
            endTime = timeline.endTime,
            isFreeTime = timeline.isFreeTime,
        )
    }
}
