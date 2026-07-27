package csh.back.domain.vote.vote.dto.response;

import csh.back.domain.trip.place.entity.TripPlace;
import csh.back.domain.trip.timeline.entity.TimeLine;
import csh.back.domain.vote.vote.entity.Vote;

import java.time.LocalDateTime;

public record VoteWithTimeLineResponse(
        Long voteId,
        Long timeLineId,
        LocalDateTime startTime,
        String confirmedPlaceName,
        String voteStatus
) {
    public static VoteWithTimeLineResponse of(TimeLine timeLine, Vote vote) {
        TripPlace tripPlace = timeLine.getConfirmedPlace();
        String confirmedPlaceName = tripPlace == null ? "" : tripPlace.getName();
        return new VoteWithTimeLineResponse(
                vote.getId(),
                timeLine.getId(),
                timeLine.getStartTime(),
                confirmedPlaceName,
                vote.getStatus().getNickname()

        );
    }
}
