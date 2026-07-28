package csh.back.domain.vote.vote.dto.web;

import csh.back.domain.trip.timeline.entity.Timeline;

public record VoteTimeLineResponse(
        Long confirmPlaceId,
        Timeline timeLine
) {
    public static VoteTimeLineResponse of(Long confirmPlaceId, Timeline timeLine) {
        return new VoteTimeLineResponse(confirmPlaceId, timeLine);
    }
}
