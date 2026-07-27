package csh.back.domain.vote.user.dto.response;

import csh.back.domain.vote.user.entity.VoteUser;

public record VoteUserSaveResponseDto(String memberName,
                                      String place,
                                      Integer updateCount) {
    public static VoteUserSaveResponseDto from(VoteUser voteUser) {
        return new VoteUserSaveResponseDto(
                voteUser.getTripMember().getMember().getName(),
                voteUser.getVoteItem().getTripPlace().getName(),
                voteUser.getUpdateCount()
        );
    }
}
