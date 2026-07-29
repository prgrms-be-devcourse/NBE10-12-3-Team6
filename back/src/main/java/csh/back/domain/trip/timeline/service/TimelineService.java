package csh.back.domain.trip.timeline.service;

import csh.back.domain.trip.group.entity.TripGroup;
import csh.back.domain.trip.group.repository.TripGroupRepository;
import csh.back.domain.trip.member.repository.TripMemberRepository;
import csh.back.domain.trip.member.validator.TripMemberValidator;
import csh.back.domain.trip.place.entity.TripPlace;
import csh.back.domain.trip.place.repository.TripPlaceRepository;
import csh.back.domain.trip.timeline.dto.request.TimelineAllCreateRequest;
import csh.back.domain.trip.timeline.dto.request.TimelineCreateRequest;
import csh.back.domain.trip.timeline.dto.request.TimelineUpdateRequest;
import csh.back.domain.trip.timeline.dto.response.TimelineCountResponse;
import csh.back.domain.trip.timeline.dto.response.TimelineResponse;
import csh.back.domain.trip.timeline.dto.response.TimelineWithVoteIdResponse;
import csh.back.domain.trip.timeline.entity.Timeline;
import csh.back.domain.trip.timeline.repository.TimelineRepository;
import csh.back.domain.vote.vote.dto.response.VoteConfirmResponse;
import csh.back.domain.vote.vote.dto.web.VoteTimelineResponse;
import csh.back.domain.vote.vote.enums.VoteStatus;
import csh.back.domain.vote.vote.repository.VoteRepository;
import csh.back.domain.vote.vote.service.VoteService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TimelineService {

    //DB 접근을 위한 Repository를 가져옴
    private final TimelineRepository timelineRepository;
    private final TripGroupRepository tripGroupRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripPlaceRepository tripPlaceRepository;
    private final VoteService voteService;
    private final TimelineEventService timelineEventService;
    private final TripMemberValidator tripMemberValidator;
    private final EntityManager entityManager;
    private final VoteRepository voteRepository;

    //최소 일차
    private static final long MINIMUM_DAY = 1L;

    //단건 타임라인 생성
    public TimelineResponse createTimeline(Long tripGroupId, Long memberId, TimelineCreateRequest request) {
        //여행 모임 방장 여부 검증
        validateTripAdmin(tripGroupId, memberId);
        //시작 시간과 종료 시간의 순서 검증
        validateStartAndEndTime(request.startTime(), request.endTime());
        //DB에 이미 저장된 타임라인과 시간이 겹치는지 검증
        validateTimelineOverlap(
                tripGroupId,
                request.dayNumber(),
                request.startTime(),
                request.endTime());

        //tripGroupId로 여행 모임 조회
        TripGroup tripGroup = findTripGroup(tripGroupId);

        //타임라인 구간 생성
        Timeline timeline = Timeline.builder()
                .tripGroup(tripGroup)
                .dayNumber(request.dayNumber())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .build();

        Timeline savedTimeline = timelineRepository.save(timeline);
        voteService.createVote(tripGroupId, memberId, savedTimeline);
        //서버에 이벤트 발송
        timelineEventService.sendTimelineUpdatedEventAfterCommit(tripGroupId, memberId);
        return TimelineResponse.from(savedTimeline);
    }

    //타임라인 시간 구간 일괄 생성
    public List<TimelineResponse> createAllTimelines(Long tripGroupId, Long memberId, TimelineAllCreateRequest request) {
        //여행 모임 방장 여부 검증
        validateTripAdmin(tripGroupId, memberId);
        //일괄 생성의 대표 일차와 각 시간 구간의 일차가 같은지 검증
        validateSameDayNumber(request);
        //각 시간 구간의 시작 시간과 종료 시간 순서 검증
        for (TimelineCreateRequest timeline : request.timelines()) {
            validateStartAndEndTime(timeline.startTime(), timeline.endTime());
        }
        //요청으로 들어온 시간 카테고리들끼리 서로 겹치는지 검증
        validateTimelineRange(request);
        //DB에 이미 저장된 타임라인과 시간이 겹치는지 검증
        for (TimelineCreateRequest timeline : request.timelines()) {
            validateTimelineOverlap(
                    tripGroupId,
                    timeline.dayNumber(),
                    timeline.startTime(),
                    timeline.endTime()
            );
        }

        //tripGroupId로 여행 모임 조회
        TripGroup tripGroup = findTripGroup(tripGroupId);

        //요청으로 들어온 시간 구간들을 Timeline 엔티티 목록으로 변환
        List<Timeline> timelines = new ArrayList<>();

        //하나씩 값을 넣음
        for (TimelineCreateRequest timeline : request.timelines()) {
            Timeline newTimeline = Timeline.builder()
                    .tripGroup(tripGroup)
                    .dayNumber(timeline.dayNumber())
                    .startTime(timeline.startTime())
                    .endTime(timeline.endTime())
                    .build();

            timelines.add(newTimeline);
        }

        //타임라인 목록을 한 번에 저장
        List<Timeline> savedTimelines = timelineRepository.saveAll(timelines);
        voteService.createVoteBatch(tripGroupId, memberId, savedTimelines);
        //서버에 이벤트 발송
        timelineEventService.sendTimelineUpdatedEventAfterCommit(tripGroupId, memberId);
        //저장된 타임라인 목록을 응답 DTO 목록으로 변환
        return savedTimelines.stream()
                .map(TimelineResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TimelineWithVoteIdResponse> getTimelines(Long tripGroupId, Long memberId, Long dayNumber) {
        //여행 모임 멤버 검증 여부 추가
        validateTripMember(tripGroupId, memberId);
        //dayNumber 검증
        if (dayNumber == null || dayNumber < MINIMUM_DAY) {
            throw new IllegalArgumentException("일차는 " + MINIMUM_DAY + " 이상이어야 합니다.");
        }
        //tripGroupId + dayNumber로 목록 조회
        List<Timeline> timelines = timelineRepository.findByTripGroupIdAndDayNumberOrderByStartTimeAsc(tripGroupId, dayNumber);
        //timelineId, voteId로 매핑된 맵을 반환
        Map<Long, Long> timelineVoteMap = voteService.findAllVoteIds(timelines.stream().map(Timeline::getId).toList());
        return timelines
                .stream()
                .map(timeline -> TimelineWithVoteIdResponse.of(timeline, timelineVoteMap.get(timeline.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TimelineCountResponse> getTimelineCounts(Long tripGroupId, Long memberId) {
        //여행 모임 멤버 검증 여부 추가
        validateTripMember(tripGroupId, memberId);
        Map<Long, Long> countMap = timelineRepository.countGroupByDayNumberId(tripGroupId)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        List<TimelineCountResponse> responses = countMap.entrySet().stream().map(
                entry -> TimelineCountResponse.of(entry.getKey(), entry.getValue()
                )
        ).toList();
        //일차별 타임라인 개수 응답 반환
        return responses;

    }

    public TimelineResponse updateTimeline(Long tripGroupId, Long timelineId, Long memberId, TimelineUpdateRequest request) {
        //여행 모임 멤버 여부 검증 추가
        validateTripMember(tripGroupId, memberId);
        //같은 여행 모임의 타임라인 시간 수정 요청을 순차적으로 처리하기 위함
        lockTripGroup(tripGroupId);
        //시작 시간과 종료 시간의 순서 검증
        validateStartAndEndTime(request.startTime(), request.endTime());

        //tripGroupId와 timelineId가 모두 일치하는 타임라인 조회
        Timeline timeline = findTimeline(tripGroupId, timelineId);
        //수정 시 자기 자신을 제외하고 DB에 이미 저장된 타임라인과 시간이 겹치는지 검증
        validateTimelineOverlapForUpdate(
                tripGroupId,
                timelineId,
                timeline.getDayNumber(),
                request.startTime(),
                request.endTime()
        );

        // 시간 범위 수정
        timeline.updateTimeRange(request.startTime(), request.endTime());
        //서버에 이벤트 발송
        timelineEventService.sendTimelineUpdatedEventAfterCommit(tripGroupId, memberId);
        // 수정된 타임라인 응답 반환
        return TimelineResponse.from(timeline);
    }

    //타임라인 삭제 메서드
    public void deleteTimeline(Long tripGroupId, Long timelineId, Long memberId) {
        //여행 모임 방장 여부 검증
        validateTripAdmin(tripGroupId, memberId);

        //tripGroupId와 timelineId가 모두 일치하는 타임라인 조회
        Timeline timeline = findTimeline(tripGroupId, timelineId);
        deleteVotesByTimeline(timelineId);
        //타임라인 제거
        timelineRepository.delete(timeline);
        //서버에 이벤트 발송
        timelineEventService.sendTimelineUpdatedEventAfterCommit(tripGroupId, memberId);

    }


    public VoteConfirmResponse confirmVote(Long tripGroupId, Long memberId, Long voteId) {

        tripMemberValidator.validMember(tripGroupId, memberId);

        Map<Long, Long> countMap = voteService.voteCount(voteId);
        long maxCount = Collections.max(countMap.values());
        List<Long> maxKeys = countMap.entrySet().stream()
                .filter(entry -> entry.getValue() == maxCount)
                .map(Map.Entry::getKey)
                .toList();
        boolean isTie = maxKeys.size() == 1 ? false : true;
        Long maxVoteItemId = maxKeys.size() == 1
                ? maxKeys.get(0)
                : maxKeys.get(ThreadLocalRandom.current().nextInt(maxKeys.size()));

        VoteTimelineResponse voteTimelineResponse = voteService.voteConfirm(maxVoteItemId, voteId);
        Long confirmPlaceId = voteTimelineResponse.confirmPlaceId();
        confirmPlaceByHost(voteTimelineResponse.timeline(), tripGroupId, confirmPlaceId);
        //        //서버에 이벤트 발송
        timelineEventService.sendTimelineUpdatedEventAfterCommit(tripGroupId, memberId);
        return VoteConfirmResponse.of(VoteStatus.CONFIRMED.getNickname(), confirmPlaceId, isTie);
    }

    public void expireAndConfirmBySystem(Long voteId) {
        // reader가 넘긴 건 detached라 id로 받음. 여기선 voteId로 집계부터.
        Map<Long, Long> countMap = voteService.voteCount(voteId);

        // ── 0표: 확정 없이 만료만 ──────────────────────────────
        if (countMap.isEmpty() || Collections.max(countMap.values()) == 0L) {
            voteService.expireVote(voteId);   // ★ 아래 설명 — Vote status를 EXPIRE로 (VoteService에 필요)
            return;
        }

        // ── 표 있음: (동점 랜덤) 확정 ──────────────────────────
        long maxCount = Collections.max(countMap.values());
        List<Long> maxKeys = countMap.entrySet().stream()
                .filter(e -> e.getValue() == maxCount)
                .map(Map.Entry::getKey)
                .toList();

        Long winnerVoteItemId = maxKeys.size() == 1
                ? maxKeys.get(0)
                : maxKeys.get(ThreadLocalRandom.current().nextInt(maxKeys.size()));

        // 수동과 동일: voteConfirm(CONFIRMED 박기 + timeline 조회) → 장소 확정
        VoteTimelineResponse res = voteService.voteConfirm(winnerVoteItemId, voteId);
        confirmPlaceBySystem(res.timeline(), res.confirmPlaceId());   // ★ tripGroupId 검증 없는 버전
    }

    // confirmPlaceByHost의 배치 버전: tripGroupId 검증 없이 findById만
    private void confirmPlaceBySystem(Timeline timeline, Long tripWishPlaceId) {
        TripPlace tripWishPlace = tripPlaceRepository.findById(tripWishPlaceId)
                .orElseThrow(() -> new IllegalStateException("확정 장소 없음: " + tripWishPlaceId));
        timeline.updateTripWishPlace(tripWishPlace);
    }

    private void confirmPlaceByHost(Timeline timeline, Long tripGroupId, Long tripWishPlaceId) {
        //tripGroupId + tripWishPlaceId로 후보 장소 조회
        TripPlace tripWishPlace = tripPlaceRepository.findByIdAndTripGroupId(tripWishPlaceId, tripGroupId)
                .orElseThrow(()-> new IllegalArgumentException("확정된 장소가 없습니다."));
        //타임라인에 확정 장소 반영
        timeline.updateTripWishPlace(tripWishPlace);
    }

    private Long resolveTripIdByVoteId(Long voteId) {
        Timeline timeline = voteRepository.findTimelineByVoteId(voteId)   // ★ 실제 조회로 교체
                .orElseThrow(() -> new IllegalStateException("Timeline 없음: voteId=" + voteId));
        return timeline.getTripGroup().getId();
    }



    private void deleteVotesByTimeline(Long timelineId) {
        entityManager.createQuery("""
                        delete from VoteUser vu
                        where vu.vote.id in (
                            select v.id from Vote v
                            where v.timeline.id = :timelineId
                        )
                        """)
                .setParameter("timelineId", timelineId)
                .executeUpdate();

        entityManager.createQuery("""
                        delete from VoteItem vi
                        where vi.vote.id in (
                            select v.id from Vote v
                            where v.timeline.id = :timelineId
                        )
                        """)
                .setParameter("timelineId", timelineId)
                .executeUpdate();

        entityManager.createQuery("""
                        delete from Vote v
                        where v.timeline.id = :timelineId
                        """)
                .setParameter("timelineId", timelineId)
                .executeUpdate();
    }

    //같은 여행 모임의 타임라인 시간 수정 요청을 순차적으로 처리하기 위한 락 메서드
    private void lockTripGroup(Long tripGroupId) {
        tripGroupRepository.findByIdWithLock(tripGroupId)
                .orElseThrow(() -> new IllegalArgumentException("여행 모임을 찾을 수 없습니다."));
    }

    // tripGroupId로 여행 모임 조회
    private TripGroup findTripGroup(Long tripGroupId) {
        return tripGroupRepository.findById(tripGroupId)
                .orElseThrow(() -> new IllegalArgumentException("여행 모임을 찾을 수 없습니다."));
    }

    //여행 모임 멤버 여부 검증
    private void validateTripMember(Long tripGroupId, Long memberId) {
        boolean isMember = tripMemberRepository.existsByTripGroupIdAndMemberId(tripGroupId, memberId);

        if (!isMember) {
            throw new IllegalArgumentException("여행 모임 멤버만 접근할 수 있습니다.");
        }
    }

    //tripGroupId와 timelineId가 모두 일치하는 타임라인 조회
    private Timeline findTimeline(Long tripGroupId, Long timelineId) {
        return timelineRepository.findByIdAndTripGroupId(timelineId, tripGroupId)
                .orElseThrow(() -> new IllegalArgumentException("타임라인을 찾을 수 없습니다."));
    }

    //방장 여부 검증
    private void validateTripAdmin(Long tripGroupId, Long memberId) {
        boolean isAdmin = tripMemberRepository.existsByTripGroupIdAndMemberIdAndIsAdminTrue(tripGroupId, memberId);

        if (!isAdmin) {
            throw new IllegalArgumentException("여행 모임 방장만 접근할 수 있습니다.");
        }
    }

    //일괄 생성 요청의 일차 번호가 모두 일치하는지 검증
    private void validateSameDayNumber(TimelineAllCreateRequest request) {
        //요청에 포함된 각 시간 구간의 일차를 대표 일차와 비교
        for (TimelineCreateRequest timeline : request.timelines()) {
            if (!request.dayNumber().equals(timeline.dayNumber())) {
                throw new IllegalArgumentException("일차 정보가 일치하지 않습니다.");
            }
        }
    }

    //시작 시간이 종료 시간보다 빠른지 검증
    private void validateStartAndEndTime(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("시작 시간과 종료 시간은 필수입니다.");
        }

        //시작 시간이 종료 시간보다 전 시간대가 아닐 경우
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("종료 시간은 시작 시간보다 늦어야 합니다.");
        }
    }

    //요청으로 들어온 timelines끼리 서로 겹치는지 검증
    private void validateTimelineRange(TimelineAllCreateRequest request) {
        //리스트를 이용해 생성된 타임라인들을 리스트로 변환해 넣음
        List<TimelineCreateRequest> timelines = new ArrayList<>(request.timelines());

        //2개씩 확인하기 위한 이중 for 문
        //하루 단위 시간 카테고리 개수는 많지 않기 때문에 모든 조합을 직접 비교
        for (int i = 0; i < timelines.size(); i++) {
            TimelineCreateRequest currentTimeline = timelines.get(i);

            for (int j = i + 1; j < timelines.size(); j++) {
                TimelineCreateRequest nextTimeline = timelines.get(j);

                //두 시간 구간이 겹치는 조건
                boolean isOverLapped =
                        //현재 시작 시간 < 다음 종료 시간 && 현재 종료 시간 > 다음 시작 시간
                        currentTimeline.startTime().isBefore(nextTimeline.endTime()) &&
                        currentTimeline.endTime().isAfter(nextTimeline.startTime());
                //하나라도 겹치는 시간 구간이 있으면 일괄 생성을 막음
                if (isOverLapped) {
                    throw new IllegalArgumentException("시간 카테고리가 겹치면 안 됩니다.");
                }
            }
        }
    }

    //DB에 이미 저장된 타임라인과 시간이 겹치는지 검증
    private void validateTimelineOverlap(Long tripGroupId, Long dayNumber, LocalDateTime startTime, LocalDateTime endTime) {
        long overlapCount = timelineRepository.countOverlappingTimeline(
                tripGroupId,
                dayNumber,
                startTime,
                endTime);

        if (overlapCount > 0) {
            throw new IllegalArgumentException("시간 카테고리가 겹치면 안 됩니다.");
        }
    }

    //수정 시 자기 자신을 제외하고 DB에 이미 저장된 타임라인과 시간이 겹치는지 검증
    private void validateTimelineOverlapForUpdate(Long tripGroupId, Long timelineId, Long dayNumber, LocalDateTime startTime, LocalDateTime endTime) {
        long overlapCount = timelineRepository.countOverlappingTimelineExceptSelf(
                tripGroupId,
                dayNumber,
                timelineId,
                startTime,
                endTime);

        if (overlapCount > 0) {
            throw new IllegalArgumentException("시간 카테고리가 겹치면 안 됩니다.");
        }
    }
}
