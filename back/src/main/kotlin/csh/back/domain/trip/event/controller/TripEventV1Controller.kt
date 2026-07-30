package csh.back.domain.trip.event.controller

import csh.back.domain.member.dto.response.AuthFilterDto
import csh.back.domain.trip.event.service.TripEventService
import csh.back.global.annotation.ApiV1
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@ApiV1
@Tag(name = "여행방 변경 알림", description = "여행방 공통 SSE API")
@RequestMapping("/trips/{tripGroupId}/events")
@RestController
class TripEventV1Controller(
    private val tripEventService: TripEventService,
) {

    @Operation(summary = "여행방 공통 변경 알림 SSE 구독")
    @GetMapping(
        value = ["/subscribe"],
        produces = [MediaType.TEXT_EVENT_STREAM_VALUE],
    )
    fun subscribe(
        @PathVariable("tripGroupId") tripGroupId: Long,
        @AuthenticationPrincipal member: AuthFilterDto,
    ): SseEmitter = tripEventService.subscribe(tripGroupId, member.id)
}
