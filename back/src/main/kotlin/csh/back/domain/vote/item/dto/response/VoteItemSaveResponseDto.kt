package csh.back.domain.vote.item.dto.response

import csh.back.domain.vote.item.entity.VoteItem

data class VoteItemSaveResponseDto(val place: String) {
    companion object {
        @JvmStatic
        fun from(voteItem: VoteItem): VoteItemSaveResponseDto =
            VoteItemSaveResponseDto(voteItem.tripPlace.name)
    }
}