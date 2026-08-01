package csh.back.domain.trip.group.settings.controller

import csh.back.domain.member.dto.response.AuthFilterDto
import csh.back.domain.trip.group.settings.dto.request.TripGroupSettingsModifyRequest
import csh.back.domain.trip.group.settings.dto.request.TripGroupSettingsUpdateRequest
import csh.back.domain.trip.group.settings.dto.response.TripGroupSettingsResponse
import csh.back.domain.trip.group.settings.service.TripGroupSettingsService
import csh.back.global.annotation.ApiV1
import csh.back.global.dto.ResponseData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@ApiV1
@Tag(name = "여행 모임 설정", description = "여행 모임 설정 관리 API")
@RestController
@RequestMapping("/trips/{tripGroupId}/settings")
class TripGroupSettingsV1Controller(
    private val tripGroupSettingsService: TripGroupSettingsService,
) {

    @Operation(summary = "여행 모임 설정 조회")
    @GetMapping
    fun getSettings(
        @PathVariable tripGroupId: Long,
        @AuthenticationPrincipal member: AuthFilterDto,
    ): ResponseData<TripGroupSettingsResponse> =
        ResponseData(
            200,
            tripGroupSettingsService.getSettings(tripGroupId, member.id),
        )

    @Operation(summary = "일차별 자유시간 범위 수정")
    @PatchMapping("/free-time/{dayNumber}")
    fun updateFreeTimeMinutes(
        @PathVariable tripGroupId: Long,
        @PathVariable dayNumber: Int,
        @AuthenticationPrincipal member: AuthFilterDto,
        @Valid @RequestBody request: TripGroupSettingsUpdateRequest,
    ): ResponseData<TripGroupSettingsResponse> =
        ResponseData(
            200,
            tripGroupSettingsService.updateFreeTimeMinutes(
                tripGroupId = tripGroupId,
                dayNumber = dayNumber,
                memberId = member.id,
                request = request,
            ),
        )

    @Operation(summary = "모든 일차 자유시간 범위 일괄 수정")
    @PatchMapping("/free-time")
    fun updateAllFreeTimeMinutes(
        @PathVariable tripGroupId: Long,
        @AuthenticationPrincipal member: AuthFilterDto,
        @Valid @RequestBody request: TripGroupSettingsUpdateRequest,
    ): ResponseData<TripGroupSettingsResponse> =
        ResponseData(
            200,
            tripGroupSettingsService.updateAllFreeTimeMinutes(
                tripGroupId = tripGroupId,
                memberId = member.id,
                request = request,
            ),
        )

    @Operation(summary = "익명/실명 투표 설정 변경", description = "방장이 변경 시 진행중인(PENDING) 투표 전체에 일괄 반영")
    @PatchMapping
    fun updateAnonymousVote(
        @PathVariable tripGroupId: Long,
        @AuthenticationPrincipal member: AuthFilterDto,
        @RequestBody request: TripGroupSettingsModifyRequest,
    ): ResponseData<TripGroupSettingsResponse> =
        ResponseData(200, tripGroupSettingsService.updateAnonymousVote(tripGroupId, member.id, request.isAnonymousVote))
}
