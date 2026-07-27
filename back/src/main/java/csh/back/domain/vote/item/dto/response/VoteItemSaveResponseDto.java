package csh.back.domain.vote.item.dto.response;

import csh.back.domain.vote.item.entity.VoteItem;

public record VoteItemSaveResponseDto(String place) {
    public static VoteItemSaveResponseDto from(VoteItem voteItem) {
        return new VoteItemSaveResponseDto(
                voteItem.getTripPlace().getName()
        );
    }
}
