package csh.back.domain.presence.controller

import csh.back.domain.member.dto.response.AuthFilterDto
import csh.back.domain.presence.service.PresenceService
import csh.back.global.annotation.ApiV1
import csh.back.global.dto.ResponseData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@ApiV1
@Tag(name = "접속 상태(Presence)", description = "사용자 온라인 상태 SSE / 조회 API")
@RestController
@RequestMapping("/presence")
class PresenceV1Controller(
    private val presenceService: PresenceService,
) {

    @Operation(summary = "내 접속 상태 SSE 구독 (로그인 후 프론트가 열고 유지)")
    @GetMapping("/sse", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribe(@AuthenticationPrincipal owner: AuthFilterDto): SseEmitter =
        presenceService.subscribe(owner.id)

    @Operation(summary = "여러 회원의 접속 상태 배치 조회 (지난 메이트 or 여행방 멤버만)")
    @GetMapping("/status")
    fun getStatus(
        @AuthenticationPrincipal owner: AuthFilterDto,
        @RequestParam userIds: List<Long>,
    ): ResponseData<Map<Long, Boolean>> =
        ResponseData(200, presenceService.getOnlineStatus(owner.id, userIds))
}
