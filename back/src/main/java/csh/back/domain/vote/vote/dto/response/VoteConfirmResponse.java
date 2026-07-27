package csh.back.domain.vote.vote.dto.response;

import csh.back.domain.vote.vote.enums.VoteConfirmStatus;

import java.util.List;

public record VoteConfirmResponse(
        String voteStatus,
        Long confirmedPlaceId,
        boolean isTie
        )
{
    public static VoteConfirmResponse of(String voteStatus, Long confirmedPlaceId, boolean isTie) {
       return new VoteConfirmResponse(
               voteStatus,
               confirmedPlaceId,
               isTie
       );
    }
}
