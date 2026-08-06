package csh.back.domain.trip.member.controller

import csh.back.domain.member.dto.response.AuthFilterDto
import csh.back.domain.trip.member.dto.response.PastMateResponse
import csh.back.domain.trip.member.service.TripMemberService
import csh.back.global.annotation.ApiV1
import csh.back.global.dto.ResponseData
import csh.back.global.dto.SliceResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@ApiV1
@Tag(name = "여행 맴버", description = "여행 멤버 관리 API")
@RestController
@RequestMapping("/trips")
class TripMemberV1Controller(
    private val tripMemberService: TripMemberService,
) {

    @Operation(summary = "초대 코드를 통한 여행 멤버 등록")
    @PostMapping("/member/{joinCode}")
    fun createJoinMember(
        @AuthenticationPrincipal owner: AuthFilterDto,
        @PathVariable joinCode: String,
    ): ResponseData<Void?> {
        tripMemberService.createJoinMember(joinCode, owner.id)
        return ResponseData(200, null)
    }

    @Operation(summary = "지난 여행 메이트 조회 (무한 스크롤, 이름 검색 옵션)")
    @GetMapping("/past-members")
    fun getPastMates(
        @AuthenticationPrincipal owner: AuthFilterDto,
        @RequestParam(name = "search", required = false) keyword: String?,
        @PageableDefault(size = 15) pageable: Pageable,
    ): ResponseData<SliceResponse<PastMateResponse>> {
        return ResponseData(200, tripMemberService.findPastMates(owner.id, keyword, pageable))
    }
}
