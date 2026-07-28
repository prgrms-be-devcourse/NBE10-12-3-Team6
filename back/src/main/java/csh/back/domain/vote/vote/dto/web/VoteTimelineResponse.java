package csh.back.domain.vote.vote.dto.web;

import csh.back.domain.trip.timeline.entity.Timeline;

public record VoteTimelineResponse(
        Long confirmPlaceId,
        Timeline timeLine
) {
    public static VoteTimelineResponse of(Long confirmPlaceId, Timeline timeLine) {
        return new VoteTimelineResponse(confirmPlaceId, timeLine);
    }
}
