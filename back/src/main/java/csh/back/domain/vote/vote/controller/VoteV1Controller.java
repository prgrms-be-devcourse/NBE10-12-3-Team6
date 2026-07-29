package csh.back.domain.vote.vote.controller;

import csh.back.domain.member.dto.response.AuthFilterDto;
import csh.back.domain.trip.timeline.service.TimelineService;
import csh.back.domain.vote.vote.dto.request.VoteCreateRequest;
import csh.back.domain.vote.vote.dto.response.*;
import csh.back.domain.vote.vote.service.VoteService;
import csh.back.global.annotation.ApiV1;
import csh.back.global.dto.ResponseData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ApiV1
@RestController
@RequiredArgsConstructor
@RequestMapping("/trips/{tripGroupId}/votes")
@Tag(name = "투표", description = "여행 모임 투표 관련 API")
public class VoteV1Controller {
    private final VoteService voteService;
    private final TimelineService timeLineService;

    @Operation(summary = "투표 목록 조회", description = "특정 여행 모임의 투표 목록을 조회")
    @GetMapping
    public ResponseData<List<VoteFindListResponse>> findVoteList(
            @PathVariable Long tripGroupId,
            @AuthenticationPrincipal AuthFilterDto member
    ) {
        return new ResponseData<>(200, voteService.findVoteList(tripGroupId, member.id()));
    }

    @Operation(summary = "투표 생성", description = "투표 생성 실패 오류 시 사용")
    @PostMapping
    public ResponseData<VoteCreateResponse> createVote(
            @PathVariable Long tripGroupId,
            @RequestBody VoteCreateRequest request,
            @AuthenticationPrincipal AuthFilterDto member
    ) {

        return new ResponseData<>(
                201,
                voteService.wrapperCreateVote(tripGroupId, member.id(), request.timeLineId())
        );
    }

    @Operation(summary = "투표 항목 및 투표 수 조회", description = "특정 투표의 장소별 항목과 투표 수 조회")
    @GetMapping("/{voteId}/count")
    public ResponseData<VoteFindWithUpdateCountResponse> findVoteItemAndCount(
            @PathVariable Long tripGroupId,
            @PathVariable Long voteId,
            @AuthenticationPrincipal AuthFilterDto member
    ) {
        return new ResponseData<>(
                200,
                voteService.findVoteItemAndCount(tripGroupId, voteId, member.id())
        );
    }

    @Operation(summary = "특정 장소 투표 참여자 조회", description = "특정 장소에 투표한 사용자 목록을 조회")
    @GetMapping("/{voteId}/places/{tripPlaceId}")
    public ResponseData<List<VoteFindUserResponse>> findVoteUserThisPlace(
            @PathVariable Long tripGroupId,
            @PathVariable Long voteId,
            @PathVariable Long tripPlaceId,
            @AuthenticationPrincipal AuthFilterDto member
    ) {
        return new ResponseData<>(200, voteService.findUserVoteThisPlace(tripGroupId, voteId, tripPlaceId, member.id()));
    }

    @Operation(summary = "투표 결과 장소 확정")
    @PatchMapping("/{voteId}/confirm")
    public ResponseData<VoteConfirmResponse> confirmVote(
            @PathVariable Long tripGroupId,
            @PathVariable Long voteId,
            @AuthenticationPrincipal AuthFilterDto member
    ) {
        //확정 장소 반영 서비스 호출
        return new ResponseData<>(200, timeLineService.confirmVote(tripGroupId, member.id(), voteId));
    }

}
