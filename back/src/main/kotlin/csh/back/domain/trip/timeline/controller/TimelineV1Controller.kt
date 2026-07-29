package csh.back.domain.trip.timeline.controller

import csh.back.domain.trip.timeline.dto.request.TimelineAllCreateRequest
import csh.back.domain.trip.timeline.dto.request.TimelineCreateRequest
import csh.back.domain.trip.timeline.dto.request.TimelineUpdateRequest
import csh.back.domain.trip.timeline.dto.response.TimelineCountResponse
import csh.back.domain.trip.timeline.dto.response.TimelineResponse
import csh.back.domain.trip.timeline.dto.response.TimelineWithVoteIdResponse
import csh.back.domain.trip.timeline.service.TimelineEventService
import csh.back.domain.trip.timeline.service.TimelineService
import csh.back.global.annotation.ApiV1
import csh.back.global.dto.ResponseData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@ApiV1
@Tag(name = "여행 타임라인", description = "여행 타임라인 시간 구간 API")
@RequestMapping("/trips/{tripGroupId}/timelines")
@RestController
class TimelineV1Controller(
    private val timelineService: TimelineService,
    private val timelineEventService: TimelineEventService,
) {

    @Operation(summary = "타임라인 시간 구간 단건 생성")
    @PostMapping
    fun createTimeline(
        @PathVariable("tripGroupId") tripGroupId: Long,
        authentication: Authentication,
        @Valid @RequestBody request: TimelineCreateRequest,
    ): ResponseData<TimelineResponse> {
        val memberId = getLoginMemberId(authentication)

        return ResponseData(
            201,
            timelineService.createTimeline(tripGroupId, memberId, request),
        )
    }

    @Tag(name = "여행 타임라인", description = "일괄 여행 타임라인 시간 구간 API")
    @Operation(summary = "타임라인 시간 구간 일괄 생성")
    @PostMapping("/batch")
    fun createAllTimelines(
        @PathVariable("tripGroupId") tripGroupId: Long,
        authentication: Authentication,
        @Valid @RequestBody request: TimelineAllCreateRequest,
    ): ResponseData<List<TimelineResponse>> {
        val memberId = getLoginMemberId(authentication)

        return ResponseData(
            201,
            timelineService.createAllTimelines(tripGroupId, memberId, request),
        )
    }

    @Operation(summary = "일차별 타임라인 시간 구간 목록 조회")
    @GetMapping
    fun getTimelines(
        @PathVariable("tripGroupId") tripGroupId: Long,
        authentication: Authentication,
        @RequestParam("dayNumber") dayNumber: Long,
    ): ResponseData<List<TimelineWithVoteIdResponse>> {
        val memberId = getLoginMemberId(authentication)

        return ResponseData(
            200,
            timelineService.getTimelines(tripGroupId, memberId, dayNumber),
        )
    }

    @Operation(summary = "타임라인 변경 알림 SSE 구독")
    @GetMapping(
        value = ["/subscribe"],
        produces = [MediaType.TEXT_EVENT_STREAM_VALUE],
    )
    fun subscribeTimeline(
        @PathVariable("tripGroupId") tripGroupId: Long,
        authentication: Authentication,
    ): SseEmitter {
        val memberId = getLoginMemberId(authentication)
        return timelineEventService.subscribe(tripGroupId, memberId)
    }

    @Operation(summary = "방 내 전체 타임라인 개수 목록 반환")
    @GetMapping("/count")
    fun getTimelineCounts(
        @PathVariable("tripGroupId") tripGroupId: Long,
        authentication: Authentication,
    ): ResponseData<List<TimelineCountResponse>> {
        val memberId = getLoginMemberId(authentication)

        return ResponseData(
            200,
            timelineService.getTimelineCounts(tripGroupId, memberId),
        )
    }

    @Operation(summary = "타임라인 시간 구간 수정")
    @PatchMapping("/{timelineId}")
    fun updateTimeline(
        @PathVariable("tripGroupId") tripGroupId: Long,
        @PathVariable("timelineId") timelineId: Long,
        authentication: Authentication,
        @Valid @RequestBody request: TimelineUpdateRequest,
    ): ResponseData<TimelineResponse> {
        val memberId = getLoginMemberId(authentication)

        return ResponseData(
            200,
            timelineService.updateTimeline(
                tripGroupId = tripGroupId,
                timelineId = timelineId,
                memberId = memberId,
                request = request,
            ),
        )
    }

    @Operation(summary = "타임라인 시간 구간 삭제")
    @DeleteMapping("/{timelineId}")
    fun deleteTimeline(
        @PathVariable("tripGroupId") tripGroupId: Long,
        @PathVariable("timelineId") timelineId: Long,
        authentication: Authentication,
    ): ResponseData<Void> {
        val memberId = getLoginMemberId(authentication)
        timelineService.deleteTimeline(tripGroupId, timelineId, memberId)

        return ResponseData(200, null)
    }

    private fun getLoginMemberId(authentication: Authentication): Long =
        authentication.details as Long
}
