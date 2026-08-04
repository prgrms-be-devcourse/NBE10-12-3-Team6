package csh.back.domain.trip.group.controller

import csh.back.domain.member.dto.response.AuthFilterDto
import csh.back.domain.trip.group.dto.request.TripGroupModifyRequest
import csh.back.domain.trip.group.dto.request.TripGroupRequest
import csh.back.domain.trip.group.dto.response.TripGroupDetailResponse
import csh.back.domain.trip.group.dto.response.TripGroupResponse
import csh.back.domain.trip.group.service.TripGroupService
import csh.back.domain.trip.member.dto.request.TripMemberInviteRequest
import csh.back.domain.trip.member.dto.response.TripMemberResponse
import csh.back.domain.trip.member.service.TripMemberService
import csh.back.global.annotation.ApiV1
import csh.back.global.dto.ResponseData
import csh.back.global.dto.SliceResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@ApiV1
@Tag(name = "여행 모임방", description = "여행 모임 관리 API")
@RestController
@RequestMapping("/trips")
class TripGroupV1Controller(
    private val tripGroupService: TripGroupService,
    private val tripMemberService: TripMemberService,
) {

    @Operation(summary = "모임방 목록 조회(로그인한 사용자 기준, 무한 스크롤)")
    @GetMapping
    fun getAllGroups(
        @RequestParam(name = "keyword", required = false) keyword: String?,
        @RequestParam(name = "startDate", required = false) startDate: String?,
        // size=10: 모바일 첫 화면에 한 번에 보이는 정도. 프론트 무한 스크롤이 hasNext를 보고 다음 요청.
        @PageableDefault(size = 10) pageable: Pageable,
        @AuthenticationPrincipal owner: AuthFilterDto,
    ): ResponseData<SliceResponse<TripGroupResponse>> {
        return ResponseData(200, tripGroupService.getGroups(owner.id, keyword, startDate, pageable))
    }

    @Operation(summary = "모임방 생성")
    @PostMapping
    fun saveGroup(
        @AuthenticationPrincipal owner: AuthFilterDto,
        @Valid @RequestBody request: TripGroupRequest,
    ): ResponseData<TripGroupResponse> {
        return ResponseData(201, tripGroupService.writeGroup(request, owner.id))
    }

    @Operation(summary = "상세 모임방 조회")
    @GetMapping("/{tripGroupId}")
    fun getGroupDetail(
        @PathVariable tripGroupId: Long,
        @AuthenticationPrincipal owner: AuthFilterDto,
    ): ResponseData<TripGroupDetailResponse> {
        return ResponseData(200, tripGroupService.getGroupDetail(tripGroupId, owner.id))
    }

    @Operation(summary = "상세 모임방 수정")
    @PatchMapping("/{tripGroupId}")
    fun modifyGroupName(
        @PathVariable tripGroupId: Long,
        @AuthenticationPrincipal owner: AuthFilterDto,
        @RequestBody request: TripGroupModifyRequest,
    ): ResponseData<TripGroupResponse> {
        return ResponseData(200, tripGroupService.modifyGroupDetail(tripGroupId, owner.id, request))
    }

    @Operation(summary = "지난 메이트를 지정해서 여행방에 초대 (방장만)")
    @PostMapping("/{tripGroupId}/members/invite")
    fun inviteMembers(
        @PathVariable tripGroupId: Long,
        @AuthenticationPrincipal owner: AuthFilterDto,
        @Valid @RequestBody request: TripMemberInviteRequest,
    ): ResponseData<List<TripMemberResponse>> {
        return ResponseData(200, tripMemberService.inviteMembers(tripGroupId, owner.id, request.memberIds))
    }
}
