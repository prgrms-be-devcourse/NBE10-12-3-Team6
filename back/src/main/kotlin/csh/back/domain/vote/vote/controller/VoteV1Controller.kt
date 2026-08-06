package csh.back.domain.vote.vote.controller

import csh.back.domain.member.dto.response.AuthFilterDto
import csh.back.domain.trip.timeline.service.TimelineService
import csh.back.domain.vote.vote.dto.request.VoteAnonymousModifyRequest
import csh.back.domain.vote.vote.dto.request.VoteCreateRequest
import csh.back.domain.vote.vote.dto.response.VoteConfirmResponse
import csh.back.domain.vote.vote.dto.response.VoteCreateResponse
import csh.back.domain.vote.vote.dto.response.VoteFindListResponse
import csh.back.domain.vote.vote.dto.response.VoteFindUserResponse
import csh.back.domain.vote.vote.dto.response.VoteFindWithUpdateCountResponse
import csh.back.domain.vote.vote.service.VoteService
import csh.back.global.dto.ResponseData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/trips/{tripGroupId}/votes")
@Tag(name = "투표", description = "여행 모임 투표 관련 API")
class VoteV1Controller(
    private val voteService: VoteService,
    private val timelineService: TimelineService,
) {

    @Operation(summary = "투표 목록 조회", description = "특정 여행 모임의 투표 목록을 조회")
    @GetMapping
    fun findVoteList(
        @PathVariable tripGroupId: Long,
        @AuthenticationPrincipal member: AuthFilterDto,
    ): ResponseData<List<VoteFindListResponse>> =
        ResponseData(200, voteService.findVoteList(tripGroupId, member.id))

    @Operation(summary = "투표 생성", description = "투표 생성 실패 오류 시 사용")
    @PostMapping
    fun createVote(
        @PathVariable tripGroupId: Long,
        @RequestBody request: VoteCreateRequest,
        @AuthenticationPrincipal member: AuthFilterDto,
    ): ResponseData<VoteCreateResponse> =
        ResponseData(201, voteService.wrapperCreateVote(tripGroupId, member.id, request.timelineId))

    @Operation(summary = "투표 항목 및 투표 수 조회", description = "특정 투표의 장소별 항목과 투표 수 조회")
    @GetMapping("/{voteId}/count")
    fun findVoteItemAndCount(
        @PathVariable tripGroupId: Long,
        @PathVariable voteId: Long,
        @AuthenticationPrincipal member: AuthFilterDto,
    ): ResponseData<VoteFindWithUpdateCountResponse> =
        ResponseData(200, voteService.findVoteItemAndCount(tripGroupId, voteId, member.id))

    @Operation(summary = "특정 장소 투표 참여자 조회", description = "특정 장소에 투표한 사용자 목록을 조회")
    @GetMapping("/{voteId}/places/{tripPlaceId}")
    fun findVoteUserThisPlace(
        @PathVariable tripGroupId: Long,
        @PathVariable voteId: Long,
        @PathVariable tripPlaceId: Long,
        @AuthenticationPrincipal member: AuthFilterDto,
    ): ResponseData<List<VoteFindUserResponse>> =
        ResponseData(200, voteService.findUserVoteThisPlace(tripGroupId, voteId, tripPlaceId, member.id))

    @Operation(summary = "투표 결과 장소 확정")
    @PatchMapping("/{voteId}/confirm")
    fun confirmVote(
        @PathVariable tripGroupId: Long,
        @PathVariable voteId: Long,
        @AuthenticationPrincipal member: AuthFilterDto,
    ): ResponseData<VoteConfirmResponse> =
        ResponseData(200, timelineService.confirmVote(tripGroupId, member.id, voteId))

    @Operation(summary = "투표 익명/실명 개별 변경", description = "방장이 특정 투표 1건의 익명 여부를 변경. 진행중인 투표만 변경 가능")
    @PatchMapping("/{voteId}/anonymous")
    fun updateAnonymous(
        @PathVariable tripGroupId: Long,
        @PathVariable voteId: Long,
        @RequestBody request: VoteAnonymousModifyRequest,
        @AuthenticationPrincipal member: AuthFilterDto,
    ): ResponseData<Boolean> =
        ResponseData(200, voteService.updateAnonymous(tripGroupId, voteId, member.id, request.isAnonymous))
}
