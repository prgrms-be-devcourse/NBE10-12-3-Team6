package csh.back.domain.vote.vote.dto.response;

import csh.back.domain.trip.place.entity.TripPlace;
import csh.back.domain.trip.timeline.entity.Timeline;
import csh.back.domain.vote.vote.entity.Vote;

import java.time.LocalDateTime;

public record VoteWithTimelineResponse(
        Long voteId,
        Long timelineId,
        LocalDateTime startTime,
        String confirmedPlaceName,
        String voteStatus
) {
    public static VoteWithTimelineResponse of(Timeline timeline, Vote vote) {
        TripPlace tripPlace = timeline.getTripWishPlace();
        String confirmedPlaceName = tripPlace == null ? "" : tripPlace.getName();
        return new VoteWithTimelineResponse(
                vote.getId(),
                timeline.getId(),
                timeline.getStartTime(),
                confirmedPlaceName,
                vote.getStatus().getNickname()

        );
    }
}
