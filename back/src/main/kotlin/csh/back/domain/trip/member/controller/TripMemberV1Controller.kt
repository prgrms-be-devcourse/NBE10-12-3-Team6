package csh.back.domain.trip.member.controller

import csh.back.domain.trip.member.service.TripMemberService
import csh.back.global.annotation.ApiV1
import csh.back.global.dto.ResponseData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
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
        authentication: Authentication,
        @PathVariable joinCode: String,
    ): ResponseData<Void> {
        val memberId = authentication.details as Long
        tripMemberService.createJoinMember(joinCode, memberId)
        return ResponseData(200, null)
    }
}
