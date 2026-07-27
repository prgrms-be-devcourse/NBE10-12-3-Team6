package csh.back.domain.vote.vote.dto.response;

import csh.back.domain.trip.place.dto.response.TripPlaceFindResponse;
import csh.back.domain.trip.timeline.entity.TimeLine;
import csh.back.domain.vote.vote.entity.Vote;
import csh.back.domain.vote.vote.enums.VoteStatus;

import java.util.List;

public record VoteFindWithUpdateCountResponse(
        List<VoteFindResponse> voteResults,
        List<TripPlaceFindResponse> wishPlaceFindResponses,
        Integer updateCount,
        String voteStatus,
        Long confirmedPlaceId
) {
    public static VoteFindWithUpdateCountResponse of(
            List<VoteFindResponse> voteFindResponses,
            List<TripPlaceFindResponse> wishPlaceFindResponses,
            int updateCount,
            Vote vote
    ) {
        VoteStatus voteStatus = vote.getStatus();
        TimeLine timeLine = vote.getTimeLine() != null ? vote.getTimeLine() : null;
        Long confirmedPlaceId = timeLine.getConfirmedPlace() != null ? timeLine.getConfirmedPlace().getId() : null;
        return new VoteFindWithUpdateCountResponse(voteFindResponses, wishPlaceFindResponses, updateCount, voteStatus.getNickname(), confirmedPlaceId);
    }
}
