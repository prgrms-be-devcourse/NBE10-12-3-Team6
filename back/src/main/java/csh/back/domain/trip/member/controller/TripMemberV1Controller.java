package csh.back.domain.trip.member.controller;

import csh.back.domain.member.dto.response.AuthFilterDto;
import csh.back.domain.member.dto.response.LoginResponseDto;
import csh.back.domain.trip.group.service.TripGroupService;
import csh.back.domain.trip.member.service.TripMemberService;
import csh.back.global.annotation.ApiV1;
import csh.back.global.dto.ResponseData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@ApiV1
@Tag(name = "여행 맴버", description = "여행 멤버 관리 API")
@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
public class TripMemberV1Controller {
	private final TripMemberService tripMemberService;

	//Swagger 문서 표시
	@Operation(summary = "초대 코드를 통한 여행 멤버 등록")
	@PostMapping("/member/{joinCode}")
	public ResponseData<Void> createJoinMember(
			@AuthenticationPrincipal AuthFilterDto member,
			@PathVariable String joinCode
	) {
		tripMemberService.createJoinMember(joinCode, member.id());
		return new ResponseData<>(200, null);
	}
}
