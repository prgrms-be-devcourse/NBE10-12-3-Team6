package csh.back.domain.trip.group.controller

import csh.back.domain.member.dto.response.AuthFilterDto
import csh.back.domain.trip.group.dto.request.TripGroupModifyRequest
import csh.back.domain.trip.group.dto.request.TripGroupRequest
import csh.back.domain.trip.group.dto.response.TripGroupDetailResponse
import csh.back.domain.trip.group.dto.response.TripGroupResponse
import csh.back.domain.trip.group.service.TripGroupService
import csh.back.global.annotation.ApiV1
import csh.back.global.dto.ResponseData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid

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
) {

    @Operation(summary = "모임방 목록 조회(로그인한 사용자 기준)")
    @GetMapping
    fun getAllGroups(
        @RequestParam(name = "keyword", required = false) keyword: String?,
        @RequestParam(name = "startDate", required = false) startDate: String?,
        @AuthenticationPrincipal owner: AuthFilterDto,
    ): ResponseData<List<TripGroupResponse>> {
        return ResponseData(200, tripGroupService.getGroups(owner.id, keyword, startDate))
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
}
