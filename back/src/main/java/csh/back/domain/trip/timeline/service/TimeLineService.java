package csh.back.domain.trip.timeline.service;

import csh.back.domain.trip.group.entity.TripGroup;
import csh.back.domain.trip.group.repository.TripGroupRepository;
import csh.back.domain.trip.member.repository.TripMemberRepository;
import csh.back.domain.trip.member.validator.TripMemberValidator;
import csh.back.domain.trip.place.entity.TripPlace;
import csh.back.domain.trip.place.repository.TripPlaceRepository;
import csh.back.domain.trip.timeline.dto.request.TimeLineAllCreateRequest;
import csh.back.domain.trip.timeline.dto.request.TimeLineCreateRequest;
import csh.back.domain.trip.timeline.dto.request.TimeLineUpdateRequest;
import csh.back.domain.trip.timeline.dto.response.TimeLineCountResponse;
import csh.back.domain.trip.timeline.dto.response.TimeLineResponse;
import csh.back.domain.trip.timeline.dto.response.TimeLineWithVoteIdResponse;
import csh.back.domain.trip.timeline.entity.TimeLine;
import csh.back.domain.trip.timeline.repository.TimeLineRepository;
import csh.back.domain.vote.vote.dto.response.VoteConfirmResponse;
import csh.back.domain.vote.vote.dto.web.VoteTimeLineResponse;
import csh.back.domain.vote.vote.enums.VoteConfirmStatus;
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
public class TimeLineService {

    //DB 접근을 위한 Repository를 가져옴
    private final TimeLineRepository timeLineRepository;
    private final TripGroupRepository tripGroupRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripPlaceRepository tripPlaceRepository;
    private final VoteService voteService;
    private final TimeLineEventService timeLineEventService;
    private final TripMemberValidator tripMemberValidator;
    private final EntityManager entityManager;
    private final VoteRepository voteRepository;

    //최소 일차
    private static final int MINIMUM_DAY = 1;

    //단건 타임라인 생성
    public TimeLineResponse createTimeLine(Long tripId, Long memberId, TimeLineCreateRequest request) {
        //여행 모임 방장 여부 검증
        validateTripAdmin(tripId, memberId);
        //시작 시간과 종료 시간의 순서 검증
        validateStartAndEndTime(request.startTime(), request.endTime());
        //DB에 이미 저장된 타임라인과 시간이 겹치는지 검증
        validateTimeLineOverlap(
                tripId,
                request.dayNumber(),
                request.startTime(),
                request.endTime());

        //tripId로 여행 모임 조회
        TripGroup tripGroup = findTripGroup(tripId);

        //타임라인 구간 생성
        TimeLine timeLine = TimeLine.builder()
                .tripGroup(tripGroup)
                .dayNumber(request.dayNumber())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .build();

        TimeLine savedTimeLine = timeLineRepository.save(timeLine);
        voteService.createVote(tripId, memberId, savedTimeLine);
        //서버에 이벤트 발송
        timeLineEventService.sendTimeLineUpdatedEventAfterCommit(tripId, memberId);
        return TimeLineResponse.from(savedTimeLine);
    }

    //타임라인 시간 구간 일괄 생성
    public List<TimeLineResponse> createAllTimeLines(Long tripId, Long memberId, TimeLineAllCreateRequest request) {
        //여행 모임 방장 여부 검증
        validateTripAdmin(tripId, memberId);
        //일괄 생성의 대표 일차와 각 시간 구간의 일차가 같은지 검증
        validateSameDayNumber(request);
        //각 시간 구간의 시작 시간과 종료 시간 순서 검증
        for (TimeLineCreateRequest timeLine : request.timeLines()) {
            validateStartAndEndTime(timeLine.startTime(), timeLine.endTime());
        }
        //요청으로 들어온 시간 카테고리들끼리 서로 겹치는지 검증
        validateTimeLineRange(request);
        //DB에 이미 저장된 타임라인과 시간이 겹치는지 검증
        for (TimeLineCreateRequest timeLine : request.timeLines()) {
            validateTimeLineOverlap(
                    tripId,
                    timeLine.dayNumber(),
                    timeLine.startTime(),
                    timeLine.endTime()
            );
        }

        //tripId로 여행 모임 조회
        TripGroup tripGroup = findTripGroup(tripId);

        //요청으로 들어온 시간 구간들을 TimeLine 엔티티 목록으로 변환
        List<TimeLine> timeLines = new ArrayList<>();

        //하나씩 값을 넣음
        for (TimeLineCreateRequest timeLine : request.timeLines()) {
            TimeLine newTimeLine = TimeLine.builder()
                    .tripGroup(tripGroup)
                    .dayNumber(timeLine.dayNumber())
                    .startTime(timeLine.startTime())
                    .endTime(timeLine.endTime())
                    .build();

            timeLines.add(newTimeLine);
        }

        //타임라인 목록을 한 번에 저장
        List<TimeLine> savedTimeLines = timeLineRepository.saveAll(timeLines);
        voteService.createVoteBatch(tripId, memberId, savedTimeLines);
        //서버에 이벤트 발송
        timeLineEventService.sendTimeLineUpdatedEventAfterCommit(tripId, memberId);
        //저장된 타임라인 목록을 응답 DTO 목록으로 변환
        return savedTimeLines.stream()
                .map(TimeLineResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TimeLineWithVoteIdResponse> getTimeLines(Long tripId, Long memberId, int dayNumber) {
        //여행 모임 멤버 검증 여부 추가
        validateTripMember(tripId, memberId);
        //dayNumber 검증
        if (dayNumber < MINIMUM_DAY) {
            throw new IllegalArgumentException("일차는 " + MINIMUM_DAY + " 이상이어야 합니다.");
        }
        //tripId + dayNumber로 목록 조회
        List<TimeLine> timeLines = timeLineRepository.findByTripGroupIdAndDayNumberOrderByStartTimeAsc(tripId, dayNumber);
        //TimeLineId,VoteId 으로 매핑된 맵을 반환
        Map<Long, Long> timeLineVoteMap = voteService.findAllVoteIds(timeLines.stream().map(TimeLine::getId).toList());
        return timeLines
                .stream()
                .map(timeLine -> TimeLineWithVoteIdResponse.of(timeLine, timeLineVoteMap.get(timeLine.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TimeLineCountResponse> getTimeLinesCount(Long tripId, Long memberId) {
        //여행 모임 멤버 검증 여부 추가
        validateTripMember(tripId, memberId);
        Map<Integer, Long> countMap = timeLineRepository.countGroupByDayNumberId(tripId)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Integer) row[0],
                        row -> (Long) row[1]
                ));

        List<TimeLineCountResponse> responses = countMap.entrySet().stream().map(
                entry -> TimeLineCountResponse.of(entry.getKey(), entry.getValue()
                )
        ).toList();
        //일차별 타임라인 개수 응답 반환
        return responses;

    }

    public TimeLineResponse updateTimeLine(Long tripId, Long timelineId, Long memberId, TimeLineUpdateRequest request) {
        //여행 모임 멤버 여부 검증 추가
        validateTripMember(tripId, memberId);
        //같은 여행 모임의 타임라인 시간 수정 요청을 순차적으로 처리하기 위함
        lockTripGroup(tripId);
        //시작 시간과 종료 시간의 순서 검증
        validateStartAndEndTime(request.startTime(), request.endTime());

        //tripId와 timelineId가 모두 일치하는 타임라인 조회
        TimeLine timeLine = findTimeLine(tripId, timelineId);
        //수정 시 자기 자신을 제외하고 DB에 이미 저장된 타임라인과 시간이 겹치는지 검증
        validateTimeLineOverlapForUpdate(
                tripId,
                timelineId,
                timeLine.getDayNumber(),
                request.startTime(),
                request.endTime()
        );

        // 시간 범위 수정
        timeLine.updateTimeRange(request.startTime(), request.endTime());
        //서버에 이벤트 발송
        timeLineEventService.sendTimeLineUpdatedEventAfterCommit(tripId, memberId);
        // 수정된 타임라인 응답 반환
        return TimeLineResponse.from(timeLine);
    }

    //타임라인 삭제 메서드
    public void deleteTimeLine(Long tripId, Long timelineId, Long memberId) {
        //여행 모임 방장 여부 검증
        validateTripAdmin(tripId, memberId);

        //tripId와 timeLineId가 모두 일치하는 타임라인 조회
        TimeLine timeLine = findTimeLine(tripId, timelineId);
        deleteVotesByTimeLine(timelineId);
        //타임라인 제거
        timeLineRepository.delete(timeLine);
        //서버에 이벤트 발송
        timeLineEventService.sendTimeLineUpdatedEventAfterCommit(tripId, memberId);

    }


    public VoteConfirmResponse confirmVote(Long tripId, Long memberId, Long voteId) {

        tripMemberValidator.validMember(tripId, memberId);

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

        VoteTimeLineResponse voteTimeLineResponse = voteService.voteConfirm(maxVoteItemId, voteId);
        Long confirmPlaceId = voteTimeLineResponse.confirmPlaceId();
        confirmPlaceByHost(voteTimeLineResponse.timeLine() ,tripId, confirmPlaceId);
        //        //서버에 이벤트 발송
        timeLineEventService.sendTimeLineUpdatedEventAfterCommit(tripId, memberId);
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

        // 수동과 동일: voteConfirm(CONFIRMED 박기 + timeLine 조회) → 장소 확정
        VoteTimeLineResponse res = voteService.voteConfirm(winnerVoteItemId, voteId);
        confirmPlaceBySystem(res.timeLine(), res.confirmPlaceId());   // ★ tripId 검증 없는 버전
    }

    // confirmPlaceByHost의 배치 버전: tripId 검증 없이 findById만
    private void confirmPlaceBySystem(TimeLine timeLine, Long confirmPlaceId) {
        TripPlace tripPlace = tripPlaceRepository.findById(confirmPlaceId)
                .orElseThrow(() -> new IllegalStateException("확정 장소 없음: " + confirmPlaceId));
        timeLine.updateConfirmedPlace(tripPlace);
    }

    private void confirmPlaceByHost(TimeLine timeLine, Long tripId, Long confirmPlaceId) {
        //tripId + confirmedPlaceId로 후보 장소 조회
        TripPlace tripPlace = tripPlaceRepository.findByIdAndTripGroupId(confirmPlaceId, tripId)
                .orElseThrow(()-> new IllegalArgumentException("확정된 장소가 없습니다."));
        //타임라인에 확정 장소 반영
        timeLine.updateConfirmedPlace(tripPlace);
    }

    private Long resolveTripIdByVoteId(Long voteId) {
        TimeLine timeLine = voteRepository.findTimeLineByVoteId(voteId)   // ★ 실제 조회로 교체
                .orElseThrow(() -> new IllegalStateException("TimeLine 없음: voteId=" + voteId));
        return timeLine.getTripGroup().getId();
    }



    private void deleteVotesByTimeLine(Long timelineId) {
        entityManager.createQuery("""
                        delete from VoteUser vu
                        where vu.vote.id in (
                            select v.id from Vote v
                            where v.timeLine.id = :timelineId
                        )
                        """)
                .setParameter("timelineId", timelineId)
                .executeUpdate();

        entityManager.createQuery("""
                        delete from VoteItem vi
                        where vi.vote.id in (
                            select v.id from Vote v
                            where v.timeLine.id = :timelineId
                        )
                        """)
                .setParameter("timelineId", timelineId)
                .executeUpdate();

        entityManager.createQuery("""
                        delete from Vote v
                        where v.timeLine.id = :timelineId
                        """)
                .setParameter("timelineId", timelineId)
                .executeUpdate();
    }

    //같은 여행 모임의 타임라인 시간 수정 요청을 순차적으로 처리하기 위한 락 메서드
    private void lockTripGroup(Long tripId) {
        tripGroupRepository.findByIdWithLock(tripId)
                .orElseThrow(() -> new IllegalArgumentException("여행 모임을 찾을 수 없습니다."));
    }

    // tripId로 여행 모임 조회
    private TripGroup findTripGroup(Long tripId) {
        return tripGroupRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("여행 모임을 찾을 수 없습니다."));
    }

    //여행 모임 멤버 여부 검증
    private void validateTripMember(Long tripId, Long memberId) {
        boolean isMember = tripMemberRepository.existsByTripGroupIdAndMemberId(tripId, memberId);

        if (!isMember) {
            throw new IllegalArgumentException("여행 모임 멤버만 접근할 수 있습니다.");
        }
    }

    //tripId와 timelineId가 모두 일치하는 타임라인 조회
    private TimeLine findTimeLine(Long tripId, Long timelineId) {
        return timeLineRepository.findByIdAndTripGroupId(timelineId, tripId)
                .orElseThrow(() -> new IllegalArgumentException("타임라인을 찾을 수 없습니다."));
    }

    //방장 여부 검증
    private void validateTripAdmin(Long tripId, Long memberId) {
        boolean isAdmin = tripMemberRepository.existsByTripGroupIdAndMemberIdAndIsAdminTrue(tripId, memberId);

        if (!isAdmin) {
            throw new IllegalArgumentException("여행 모임 방장만 접근할 수 있습니다.");
        }
    }

    //일괄 생성 요청의 일차 번호가 모두 일치하는지 검증
    private void validateSameDayNumber(TimeLineAllCreateRequest request) {
        //요청에 포함된 각 시간 구간의 일차를 대표 일차와 비교
        for (TimeLineCreateRequest timeLine : request.timeLines()) {
            if (!request.dayNumber().equals(timeLine.dayNumber())) {
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

    //요청으로 들어온 timeLines 끼리 서로 겹치는지 검증
    private void validateTimeLineRange(TimeLineAllCreateRequest request) {
        //리스트를 이용해 생성된 타임라인들을 리스트로 변환해 넣음
        List<TimeLineCreateRequest> timeLines = new ArrayList<>(request.timeLines());

        //2개씩 확인하기 위한 이중 for 문
        //하루 단위 시간 카테고리 개수는 많지 않기 때문에 모든 조합을 직접 비교
        for (int i = 0; i <timeLines.size(); i++) {
            TimeLineCreateRequest currentTimeLine = timeLines.get(i);

            for (int j = i+1; j < timeLines.size(); j++) {
                TimeLineCreateRequest nextTimeLine = timeLines.get(j);

                //두 시간 구간이 겹치는 조건
                boolean isOverLapped =
                        //현재 시작 시간 < 다음 종료 시간 && 현재 종료 시간 > 다음 시작 시간
                        currentTimeLine.startTime().isBefore(nextTimeLine.endTime()) &&
                        currentTimeLine.endTime().isAfter(nextTimeLine.startTime());
                //하나라도 겹치는 시간 구간이 있으면 일괄 생성을 막음
                if (isOverLapped) {
                    throw new IllegalArgumentException("시간 카테고리가 겹치면 안 됩니다.");
                }
            }
        }
    }

    //DB에 이미 저장된 타임라인과 시간이 겹치는지 검증
    private void validateTimeLineOverlap(Long tripId, Integer dayNumber, LocalDateTime startTime, LocalDateTime endTime) {
        long overlapCount = timeLineRepository.countOverlappingTimeLine(
                tripId,
                dayNumber,
                startTime,
                endTime);

        if (overlapCount > 0) {
            throw new IllegalArgumentException("시간 카테고리가 겹치면 안 됩니다.");
        }
    }

    //수정 시 자기 자신을 제외하고 DB에 이미 저장된 타임라인과 시간이 겹치는지 검증
    private void validateTimeLineOverlapForUpdate(Long tripId, Long timelineId, Integer dayNumber, LocalDateTime startTime, LocalDateTime endTime) {
        long overlapCount = timeLineRepository.countOverlappingTimeLineExceptSelf(
                tripId,
                dayNumber,
                timelineId,
                startTime,
                endTime);

        if (overlapCount > 0) {
            throw new IllegalArgumentException("시간 카테고리가 겹치면 안 됩니다.");
        }
    }
}
