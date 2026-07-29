package csh.back.domain.trip.timeline.controller;

import csh.back.domain.trip.timeline.dto.request.TimelineAllCreateRequest;
import csh.back.domain.trip.timeline.dto.request.TimelineCreateRequest;
import csh.back.domain.trip.timeline.dto.request.TimelineUpdateRequest;
import csh.back.domain.trip.timeline.dto.response.TimelineCountResponse;
import csh.back.domain.trip.timeline.dto.response.TimelineResponse;
import csh.back.domain.trip.timeline.dto.response.TimelineWithVoteIdResponse;
import csh.back.domain.trip.timeline.service.TimelineEventService;
import csh.back.domain.trip.timeline.service.TimelineService;
import csh.back.global.annotation.ApiV1;
import csh.back.global.dto.ResponseData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@ApiV1
@Tag(name = "여행 타임라인", description = "여행 타임라인 시간 구간 API")
@RequiredArgsConstructor
//공통 URL 경로 설정
@RequestMapping("/trips/{tripGroupId}/timelines")
//JSON 응답을 반환하는 REST API 컨트롤러
@RestController
public class TimelineV1Controller {

    //타임라인 관련 비즈니스 로직을 처리하는 Service
    private final TimelineService timelineService;
    private final TimelineEventService timelineEventService;

    @Operation(summary = "타임라인 시간 구간 단건 생성")
    //타임라인 시간 구간 등록
    @PostMapping
    public ResponseData<TimelineResponse> createTimeline(
            @PathVariable Long tripGroupId,
            Authentication authentication,
            @Valid @RequestBody TimelineCreateRequest request) {
        //로그인 정보에서 가져옴
        Long memberId = getLoginMemberId(authentication);
        //ResponseData로 감싸서 201 Created 반환
        return new ResponseData<>(201, timelineService.createTimeline(tripGroupId, memberId, request));
    }

    //타임라인 시간 구간 일괄 생성
    @Tag(name = "여행 타임라인", description = "일괄 여행 타임라인 시간 구간 API")
    @Operation(summary = "타임라인 시간 구간 일괄 생성")
    @PostMapping("/batch")
    public ResponseData<List<TimelineResponse>> createAllTimelines(
            @PathVariable Long tripGroupId,
            Authentication authentication,
            @Valid @RequestBody TimelineAllCreateRequest request) {

        //로그인 정보에서 가져옴
        Long memberId = getLoginMemberId(authentication);
        //ResponseData로 감싸서 201 Created 반환
        return new ResponseData<>(201, timelineService.createAllTimelines(tripGroupId, memberId, request));
    }

    @Operation(summary = "일차별 타임라인 시간 구간 목록 조회")
    //특정 여행 모임의 특정 일차 타임라인 목록 조회
    @GetMapping
    public ResponseData<List<TimelineWithVoteIdResponse>> getTimelines(
            @PathVariable Long tripGroupId,
            Authentication authentication,
            @RequestParam Long dayNumber) {
        //로그인 정보에서 가져옴
        Long memberId = getLoginMemberId(authentication);
        //ResponseData로 감싸서 200 OK 반환
        return new ResponseData<>(200, timelineService.getTimelines(tripGroupId, memberId, dayNumber));
    }

    //타임라인 변경 알림 SSE 구독
    @Operation(summary = "타임라인 변경 알림 SSE 구독")
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeTimeline(
            @PathVariable Long tripGroupId,
            Authentication authentication) {
        //로그인 정보에서 가져옴
        Long memberId = getLoginMemberId(authentication);
        //여행 모임 멤버 검증 후 SSE 연결 생성
        return timelineEventService.subscribe(tripGroupId, memberId);

    }

    @Operation(summary = "방 내 전체 타임라인 개수 목록 반환")
    //방 내 전체 타임라인 개수 목록 조회
    @GetMapping("/count")
    public ResponseData<List<TimelineCountResponse>> getTimelineCounts(
            @PathVariable Long tripGroupId,
            Authentication authentication) {
        //로그인 정보에서 가져옴
        Long memberId = getLoginMemberId(authentication);
        //ResponseData로 감싸서 200 OK 반환
        return new ResponseData<>(200, timelineService.getTimelineCounts(tripGroupId, memberId));
    }

    @Operation(summary = "타임라인 시간 구간 수정")
    //특정 타임라인 시간 구간 수정
    @PatchMapping("/{timelineId}")
    public ResponseData<TimelineResponse> updateTimeline(
            @PathVariable Long tripGroupId,
            @PathVariable Long timelineId,
            Authentication authentication,
            @Valid @RequestBody TimelineUpdateRequest request) {
        //로그인 정보에서 가져옴
        Long memberId = getLoginMemberId(authentication);
        return new ResponseData<>(200, timelineService.updateTimeline(tripGroupId, timelineId, memberId, request));
    }

    @Operation(summary = "타임라인 시간 구간 삭제")
    //특정 타임라인 시간 구간 삭제
    @DeleteMapping("/{timelineId}")
    public ResponseData<Void> deleteTimeline(
            @PathVariable Long tripGroupId,
            @PathVariable Long timelineId,
            Authentication authentication) {
        //로그인 정보에서 가져옴
        Long memberId = getLoginMemberId(authentication);
        timelineService.deleteTimeline(tripGroupId, timelineId, memberId);

        return new ResponseData<>(200, null);
    }

    //로그인 인증 정보에서 memberId를 가져오는 메서드
    private Long getLoginMemberId(Authentication authentication) {
        return (Long) authentication.getDetails();
    }
}
