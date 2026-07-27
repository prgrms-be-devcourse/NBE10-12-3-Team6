package csh.back.domain.trip.group.controller;

import csh.back.domain.member.dto.response.AuthFilterDto;
import csh.back.domain.trip.group.dto.request.TripGroupModifyRequest;
import csh.back.domain.trip.group.dto.request.TripGroupRequest;
import csh.back.domain.trip.group.dto.response.TripGroupDetailResponse;
import csh.back.domain.trip.group.dto.response.TripGroupResponse;
import csh.back.domain.trip.group.exception.NotFoundException;
import csh.back.domain.trip.group.service.TripGroupService;
import csh.back.global.annotation.ApiV1;
import csh.back.global.dto.ResponseData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@ApiV1
@Tag(name = "여행 모임방", description = "여행 모임 관리 API")
@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
public class TripGroupV1Controller {

	private final TripGroupService tripGroupService;

	//FIXME 정렬 및 여러 검색어로 조회가 가능하게 동시적

	//Swagger 문서 표시
	@Operation(summary = "모임방 목록 조회(로그인한 사용자 기준)")
	//모임방 조회
	@GetMapping()
	public ResponseData<List<TripGroupResponse>> getAllGroups(
			@RequestParam(name = "keyword", required = false) String keyword,
			@RequestParam(name = "startDate", required = false) String startDate,
			@AuthenticationPrincipal AuthFilterDto owner
	) {
		log.info("owner = {}", owner);
		return new ResponseData<>(200, tripGroupService.getGroups(owner.id(), keyword, startDate));
	}

	//Swagger 문서 표시
	@Operation(summary = "모임방 생성")
	//모임방 생성
	@PostMapping()
	public ResponseData<TripGroupResponse> saveGroup(
			@AuthenticationPrincipal AuthFilterDto owner,
			@Valid @RequestBody TripGroupRequest request
	) {
		return new ResponseData<>(201, tripGroupService.writeGroup(request, owner.id()));
	}

	//Swagger 문서 표시
	@Operation(summary = "상세 모임방 조회")
	//모임방 상세페이지 조회
	@GetMapping("/{tripGroupId}")
	public ResponseData<TripGroupDetailResponse> getGroupDetail(
			@PathVariable Long tripGroupId,
			@AuthenticationPrincipal AuthFilterDto owner
	) {
		return new ResponseData<>(200, tripGroupService.getGroupDetail(tripGroupId, owner.id()));
	}

	//Swagger 문서 표시
	@Operation(summary = "상세 모임방 수정")
	//모임방 상세 수정 - name
	@PatchMapping("/{tripGroupId}")
	public ResponseData<TripGroupResponse> modifyGroupName(
			@PathVariable Long tripGroupId,
			@AuthenticationPrincipal AuthFilterDto owner,
			@RequestBody TripGroupModifyRequest request
			) {
		return new ResponseData<>(200, tripGroupService.modifyGroupDetail(tripGroupId, owner.id(), request));
	}
}
