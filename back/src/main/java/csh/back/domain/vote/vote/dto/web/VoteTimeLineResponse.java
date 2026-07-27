package csh.back.domain.vote.vote.dto.web;

import csh.back.domain.trip.timeline.entity.TimeLine;

public record VoteTimeLineResponse(
        Long confirmPlaceId,
        TimeLine timeLine
) {
    public static VoteTimeLineResponse of(Long confirmPlaceId, TimeLine timeLine) {
        return new VoteTimeLineResponse(confirmPlaceId, timeLine);
    }
}
