package csh.back.domain.vote.vote.dto.response;

import csh.back.domain.trip.place.entity.TripPlace;
import csh.back.domain.trip.timeline.entity.Timeline;
import csh.back.domain.vote.vote.entity.Vote;

import java.time.LocalDateTime;

public record VoteWithTimelineResponse(
        Long voteId,
        Long timeLineId,
        LocalDateTime startTime,
        String confirmedPlaceName,
        String voteStatus
) {
    public static VoteWithTimelineResponse of(Timeline timeLine, Vote vote) {
        TripPlace tripPlace = timeLine.getConfirmedPlace();
        String confirmedPlaceName = tripPlace == null ? "" : tripPlace.getName();
        return new VoteWithTimelineResponse(
                vote.getId(),
                timeLine.getId(),
                timeLine.getStartTime(),
                confirmedPlaceName,
                vote.getStatus().getNickname()

        );
    }
}
