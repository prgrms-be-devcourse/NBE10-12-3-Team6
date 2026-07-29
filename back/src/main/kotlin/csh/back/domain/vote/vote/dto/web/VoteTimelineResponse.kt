package csh.back.domain.vote.vote.dto.web

import csh.back.domain.trip.timeline.entity.Timeline

data class VoteTimelineResponse(
    val confirmPlaceId: Long,
    val timeline: Timeline,
) {
    companion object {
        @JvmStatic
        fun of(confirmPlaceId: Long, timeline: Timeline): VoteTimelineResponse =
            VoteTimelineResponse(confirmPlaceId, timeline)
    }
}
