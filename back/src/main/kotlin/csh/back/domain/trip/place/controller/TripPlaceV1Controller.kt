package csh.back.domain.trip.place.controller

import csh.back.domain.member.dto.response.AuthFilterDto
import csh.back.domain.trip.place.dto.request.TripPlaceSaveRequest
import csh.back.domain.trip.place.dto.response.TripPlaceFindResponse
import csh.back.domain.trip.place.dto.response.TripPlaceSaveResponse
import csh.back.domain.trip.place.service.TripPlaceService
import csh.back.global.annotation.ApiV1
import csh.back.global.dto.ResponseData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@ApiV1
@RequestMapping("/trips")
@RestController
@Tag(name = "여행 위시 장소", description = "여행 모임 위시 장소 관련 API")
class TripPlaceV1Controller(
    private val tripPlaceService: TripPlaceService,
) {

    @Operation(summary = "위시 장소 목록 조회")
    @GetMapping("/{tripGroupId}/wish-places")
    fun findWishPlaces(
        @PathVariable tripGroupId: Long,
        @AuthenticationPrincipal member: AuthFilterDto,
    ): ResponseData<List<TripPlaceFindResponse>> = ResponseData(
        200,
        tripPlaceService.findWishPlaces(tripGroupId, member.id),
    )

    @Operation(summary = "위시 장소 저장")
    @PostMapping("/{tripGroupId}/wish-places")
    fun saveWishPlace(
        @RequestBody request: TripPlaceSaveRequest,
        @PathVariable tripGroupId: Long,
        @AuthenticationPrincipal member: AuthFilterDto,
    ): ResponseData<TripPlaceSaveResponse> = ResponseData(
        200,
        tripPlaceService.savePlace(
            tripGroupId,
            request.name,
            request.category,
            request.address,
            request.kakaoPlaceId,
            request.kakaoMapUrl,
            member.id,
        ),
    )
}