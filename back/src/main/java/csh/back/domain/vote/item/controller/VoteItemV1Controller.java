package csh.back.domain.vote.item.controller;

import csh.back.domain.member.dto.response.AuthFilterDto;
import csh.back.domain.vote.item.dto.request.VoteItemSaveRequestDto;
import csh.back.domain.vote.item.service.VoteItemService;
import csh.back.domain.vote.user.dto.response.VoteUserSaveResponseDto;
import csh.back.global.dto.ResponseData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/votes/{voteId}")
@RequiredArgsConstructor
@Tag(name = "투표 참여", description = "투표 항목 선택(투표 참여) 관련 API")
public class VoteItemV1Controller {
    private final VoteItemService voteItemService;

    @Operation(summary = "투표 참여")
    @PostMapping
    public ResponseData<VoteUserSaveResponseDto> saveVote(
            @PathVariable Long tripId,
            @PathVariable Long voteId,
            @RequestBody VoteItemSaveRequestDto request,
            @AuthenticationPrincipal AuthFilterDto member) {
        return new ResponseData<>(
                201,
                voteItemService.saveVoteItem(tripId, member.id(), voteId, request.placeId())
        );
    }
}
