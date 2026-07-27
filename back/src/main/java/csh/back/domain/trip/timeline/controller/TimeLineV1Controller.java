package csh.back.domain.trip.timeline.controller;

import csh.back.domain.trip.timeline.dto.request.TimeLineAllCreateRequest;
import csh.back.domain.trip.timeline.dto.request.TimeLineCreateRequest;
import csh.back.domain.trip.timeline.dto.request.TimeLineUpdateRequest;
import csh.back.domain.trip.timeline.dto.response.TimeLineCountResponse;
import csh.back.domain.trip.timeline.dto.response.TimeLineResponse;
import csh.back.domain.trip.timeline.dto.response.TimeLineWithVoteIdResponse;
import csh.back.domain.trip.timeline.service.TimeLineEventService;
import csh.back.domain.trip.timeline.service.TimeLineService;
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
@RequestMapping("/trips/{tripId}/timelines")
//JSON 응답을 반환하는 REST API 컨트롤러
@RestController
public class TimeLineV1Controller {

    //타임라인 관련 비즈니스 로직을 처리하는 Service
    private final TimeLineService timeLineService;
    private final TimeLineEventService timeLineEventService;

    @Operation(summary = "타임라인 시간 구간 단건 생성")
    //타임라인 시간 구간 등록
    @PostMapping
    public ResponseData<TimeLineResponse> createTimeLine(
            @PathVariable Long tripId,
            Authentication authentication,
            @Valid @RequestBody TimeLineCreateRequest request) {
        //로그인 정보에서 가져옴
        Long memberId = getLoginMemberId(authentication);
        //ResponseData로 감싸서 201 Created 반환
        return new ResponseData<>(201, timeLineService.createTimeLine(tripId, memberId, request));
    }

    //타임라인 시간 구간 일괄 생성
    @Tag(name = "여행 타임라인", description = "일괄 여행 타임라인 시간 구간 API")
    @Operation(summary = "타임라인 시간 구간 일괄 생성")
    @PostMapping("/batch")
    public ResponseData<List<TimeLineResponse>> createAllTimeLines(
            @PathVariable Long tripId,
            Authentication authentication,
            @Valid @RequestBody TimeLineAllCreateRequest request) {

        //로그인 정보에서 가져옴
        Long memberId = getLoginMemberId(authentication);
        //ResponseData로 감싸서 201 Created 반환
        return new ResponseData<>(201, timeLineService.createAllTimeLines(tripId, memberId, request));
    }

    @Operation(summary = "일차별 타임라인 시간 구간 목록 조회")
    //특정 여행 모임의 특정 일차 타임라인 목록 조회
    @GetMapping
    public ResponseData<List<TimeLineWithVoteIdResponse>> getTimeLines(
            @PathVariable Long tripId,
            Authentication authentication,
            @RequestParam int dayNumber) {
        //로그인 정보에서 가져옴
        Long memberId = getLoginMemberId(authentication);
        //ResponseData로 감싸서 200 OK 반환
        return new ResponseData<>(200, timeLineService.getTimeLines(tripId, memberId, dayNumber));
    }

    //타임라인 변경 알림 SSE 구독
    @Operation(summary = "타임라인 변경 알림 SSE 구독")
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeTimeLine(
            @PathVariable Long tripId,
            Authentication authentication) {
        //로그인 정보에서 가져옴
        Long memberId = getLoginMemberId(authentication);
        //여행 모임 멤버 검증 후 SSE 연결 생성
        return timeLineEventService.subscribe(tripId, memberId);

    }

    @Operation(summary = "방 내 전체 타임라인 개수 목록 반환")
    //방 내 전체 타임라인 개수 목록 조회
    @GetMapping("/count")
    public ResponseData<List<TimeLineCountResponse>> getTimeLinesCount(
            @PathVariable Long tripId,
            Authentication authentication) {
        //로그인 정보에서 가져옴
        Long memberId = getLoginMemberId(authentication);
        //ResponseData로 감싸서 200 OK 반환
        return new ResponseData<>(200, timeLineService.getTimeLinesCount(tripId, memberId));
    }

    @Operation(summary = "타임라인 시간 구간 수정")
    //특정 타임라인 시간 구간 수정
    @PatchMapping("/{timelineId}")
    public ResponseData<TimeLineResponse> updateTimeLine(
            @PathVariable Long tripId,
            @PathVariable Long timelineId,
            Authentication authentication,
            @Valid @RequestBody TimeLineUpdateRequest request) {
        //로그인 정보에서 가져옴
        Long memberId = getLoginMemberId(authentication);
        return new ResponseData<>(200, timeLineService.updateTimeLine(tripId, timelineId, memberId, request));
    }

    @Operation(summary = "타임라인 시간 구간 삭제")
    //특정 타임라인 시간 구간 삭제
    @DeleteMapping("/{timelineId}")
    public ResponseData<Void> deleteTimeLine(
            @PathVariable Long tripId,
            @PathVariable Long timelineId,
            Authentication authentication) {
        //로그인 정보에서 가져옴
        Long memberId = getLoginMemberId(authentication);
        timeLineService.deleteTimeLine(tripId, timelineId, memberId);

        return new ResponseData<>(200, null);
    }

    //로그인 인증 정보에서 memberId를 가져오는 메서드
    private Long getLoginMemberId(Authentication authentication) {
        return (Long) authentication.getDetails();
    }
}
