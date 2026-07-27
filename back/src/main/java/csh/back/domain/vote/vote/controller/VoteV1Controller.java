package csh.back.domain.vote.vote.controller;

import csh.back.domain.member.dto.response.AuthFilterDto;
import csh.back.domain.trip.timeline.service.TimeLineService;
import csh.back.domain.vote.vote.dto.request.VoteConfirmPlaceRequest;
import csh.back.domain.vote.vote.dto.request.VoteCreateRequest;
import csh.back.domain.vote.vote.dto.response.*;
import csh.back.domain.vote.vote.service.VoteService;
import csh.back.global.annotation.ApiV1;
import csh.back.global.dto.ResponseData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ApiV1
@RestController
@RequiredArgsConstructor
@RequestMapping("/trips/{tripId}/votes")
@Tag(name = "투표", description = "여행 모임 투표 관련 API")
public class VoteV1Controller {
    private final VoteService voteService;
    private final TimeLineService timeLineService;

    @Operation(summary = "투표 목록 조회", description = "특정 여행 모임의 투표 목록을 조회")
    @GetMapping
    public ResponseData<List<VoteFindListResponse>> findVoteList(
            @PathVariable Long tripId,
            @AuthenticationPrincipal AuthFilterDto member
    ) {
        return new ResponseData<>(200, voteService.findVoteList(tripId, member.id()));
    }

    @Operation(summary = "투표 생성", description = "투표 생성 실패 오류 시 사용")
    @PostMapping
    public ResponseData<VoteCreateResponse> createVote(
            @PathVariable Long tripId,
            @RequestBody VoteCreateRequest request,
            @AuthenticationPrincipal AuthFilterDto member
    ) {

        return new ResponseData<>(
                201,
                voteService.wrapperCreateVote(tripId, member.id(), request.timeLineId())
        );
    }

    @Operation(summary = "투표 항목 및 투표 수 조회", description = "특정 투표의 장소별 항목과 투표 수 조회")
    @GetMapping("/{voteId}/count")
    public ResponseData<VoteFindWithUpdateCountResponse> findVoteItemAndCount(
            @PathVariable Long tripId,
            @PathVariable Long voteId,
            @AuthenticationPrincipal AuthFilterDto member
    ) {
        return new ResponseData<>(
                200,
                voteService.findVoteItemAndCount(tripId, voteId, member.id())
        );
    }

    @Operation(summary = "특정 장소 투표 참여자 조회", description = "특정 장소에 투표한 사용자 목록을 조회")
    @GetMapping("/{voteId}/places/{placeId}")
    public ResponseData<List<VoteFindUserResponse>> findVoteUserThisPlace(
            @PathVariable Long tripId,
            @PathVariable Long voteId,
            @PathVariable Long placeId,
            @AuthenticationPrincipal AuthFilterDto member
    ) {
        return new ResponseData<>(200, voteService.findUserVoteThisPlace(tripId, voteId, placeId, member.id()));
    }

    @Operation(summary = "투표 결과 장소 확정")
    @PatchMapping("/{voteId}/confirm")
    public ResponseData<VoteConfirmResponse> confirmVote(
            @PathVariable Long tripId,
            @PathVariable Long voteId,
            @AuthenticationPrincipal AuthFilterDto member
    ) {
        //확정 장소 반영 서비스 호출
        return new ResponseData<>(200, timeLineService.confirmVote(tripId, member.id(), voteId));
    }

}
