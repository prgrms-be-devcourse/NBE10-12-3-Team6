package csh.back.domain.trip.place.controller;

import csh.back.domain.member.dto.response.AuthFilterDto;
import csh.back.domain.trip.place.dto.request.TripPlaceSaveRequest;
import csh.back.domain.trip.place.dto.response.TripPlaceFindResponse;
import csh.back.domain.trip.place.dto.response.TripPlaceSaveResponse;
import csh.back.domain.trip.place.service.TripPlaceService;

import csh.back.global.annotation.ApiV1;
import csh.back.global.dto.ResponseData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@ApiV1
@RequestMapping("/trips")
@RequiredArgsConstructor
@RestController
@Tag(name = "여행 위시 장소", description = "여행 모임 위시 장소 관련 API")
public class TripPlaceV1Controller {
    private final TripPlaceService tripPlaceService;

    @Operation(summary = "위시 장소 목록 조회")
    @GetMapping("/{tripId}/wish-places")
    public ResponseData<List<TripPlaceFindResponse>> findWishPlaces(
            @PathVariable Long tripId,
            @AuthenticationPrincipal AuthFilterDto member) {
        return new ResponseData<>(
                200,
                tripPlaceService.findWishPlaces(tripId, member.id())
        );
    }


    @Operation(summary = "위시 장소 저장")
    @PostMapping("/{tripId}/wish-places")
    public ResponseData<TripPlaceSaveResponse> saveWishPlace(
            @RequestBody TripPlaceSaveRequest request,
            @PathVariable Long tripId,
            @AuthenticationPrincipal AuthFilterDto member) {
        return new ResponseData(
                200,
                tripPlaceService.savePlace(
                        tripId,
                        request.name(),
                        request.category(),
                        request.address(),
                        request.kakaoPlaceId(),
                        request.kakaoMapUrl(),
                        member.id()
                )
        );
    }
}
