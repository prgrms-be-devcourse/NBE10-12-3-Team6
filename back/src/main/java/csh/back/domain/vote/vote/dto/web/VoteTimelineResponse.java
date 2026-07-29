package csh.back.domain.vote.vote.dto.web;

import csh.back.domain.trip.timeline.entity.Timeline;

public record VoteTimelineResponse(
        Long confirmPlaceId,
        Timeline timeline
) {
    public static VoteTimelineResponse of(Long confirmPlaceId, Timeline timeline) {
        return new VoteTimelineResponse(confirmPlaceId, timeline);
    }
}
