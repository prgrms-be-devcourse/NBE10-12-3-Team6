package csh.back.domain.vote.vote.dto.response;

import csh.back.domain.vote.user.entity.VoteUser;
import csh.back.domain.vote.vote.entity.Vote;

public record VoteFindUserResponse(String name) {
    public static VoteFindUserResponse from(VoteUser voteUser) {
        return new VoteFindUserResponse(voteUser.getTripMember().getMember().getName());
    }
}
