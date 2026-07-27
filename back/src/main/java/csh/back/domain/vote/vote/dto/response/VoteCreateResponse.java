package csh.back.domain.vote.vote.dto.response;

import csh.back.domain.vote.vote.entity.Vote;

public record VoteCreateResponse(
        Long voteId
) {
    public static VoteCreateResponse from(Vote vote) {
        return new VoteCreateResponse(vote.getId());
    }
}
