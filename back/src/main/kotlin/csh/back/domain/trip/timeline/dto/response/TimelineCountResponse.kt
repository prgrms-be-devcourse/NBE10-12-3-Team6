package csh.back.domain.trip.timeline.dto.response

data class TimelineCountResponse(
    val day: Long,
    val count: Long,
) {
    companion object {
        @JvmStatic
        fun of(day: Long, count: Long): TimelineCountResponse =
            TimelineCountResponse(day = day, count = count)
    }
}
