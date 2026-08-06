package csh.back.domain.vote.item.controller

import csh.back.domain.member.dto.response.AuthFilterDto
import csh.back.domain.vote.item.dto.request.VoteItemSaveRequestDto
import csh.back.domain.vote.item.service.VoteItemService
import csh.back.domain.vote.user.dto.response.VoteUserSaveResponseDto
import csh.back.global.dto.ResponseData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/trips/{tripGroupId}/votes/{voteId}")
@Tag(name = "투표 참여", description = "투표 항목 선택(투표 참여) 관련 API")
class VoteItemV1Controller(
    private val voteItemService: VoteItemService,
) {

    @Operation(summary = "투표 참여")
    @PostMapping
    fun saveVote(
        @PathVariable tripGroupId: Long,
        @PathVariable voteId: Long,
        @RequestBody request: VoteItemSaveRequestDto,
        @AuthenticationPrincipal member: AuthFilterDto,
    ): ResponseData<VoteUserSaveResponseDto> = ResponseData(
        201,
        voteItemService.saveVoteItem(tripGroupId, member.id, voteId, request.tripPlaceId),
    )
}