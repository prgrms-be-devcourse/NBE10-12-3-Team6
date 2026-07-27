package csh.back.domain.trip.timeline.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@RequiredArgsConstructor
@Service
public class TimeLineEventService {
    //SSE 요청은 오래 유지되므로 JPA 영속성 컨텍스트를 열지 않고 짧은 JDBC 조회로 멤버 여부만 검증
    private final JdbcTemplate jdbcTemplate;

    //SSE 연결 유지 시간
    //60초 * 60분 * 1000ms = 3,600,000ms -> 1시간 유지
    //끊어지면 프론트가 재접속 요청
    private static final Long DEFAULT_TIMEOUT = 60L * 60L * 1000L;
    //tripId별 SSE 연결 목록 저장
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    //특정 여행 모임의 타임라인 변경 이벤트를 구독
    public SseEmitter subscribe(Long tripId, Long memberId) {
        //여행 모임 멤버 여부 검증
        validateTripMember(tripId, memberId);
        //SSE 연결 객체 생성, 설정한 시간 동안 연결 유지
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        //tripId에 해당하는 연결 목록이 없으면 새로 만들고, 있으면 기존 목록 사용
        //해당 여행 모임을 보고 있는 사용자의 SSE 연결을 목록에 추가
        emitters.computeIfAbsent(tripId, key -> new CopyOnWriteArrayList<>())
                .add(emitter);
        //SSE 연결이 정상 종료되면 연결 목록에서 제거
        emitter.onCompletion(() -> removeEmitter(tripId, emitter));
        //SSE 연결 시간이 초과되면 연결 목록에서 제거
        emitter.onTimeout(() -> removeEmitter(tripId, emitter));
        //SSE 연결 중 에러가 발생하면 연결 목록에서 제거
        emitter.onError(error -> removeEmitter(tripId, emitter));

        //연결 직후 프론트가 정상 연결 여부를 확인할 수 있도록 최초 이벤트 전송
        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data("타임라인 변경 알림 연결이 완료되었습니다."));
        } catch (IOException e) {
            removeEmitter(tripId, emitter);
            emitter.completeWithError(e);
        }

        //생성한 SSE 연결 객체 반환
        return emitter;
    }

    //특정 여행 모임의 타임라인이 변경되었음을 구독 중인 사용자들에게 전송
    private void sendTimeLineUpdatedEvent(Long tripId, Long changedMemberId) {
        //tripId에 해당하는 SSE 연결 목록 조회
        List<SseEmitter> tripEmitters = emitters.get(tripId);

        //해당 여행 모임의 연결 목록이 없거나 비어 있으면 그대로 종료
        if (tripEmitters == null || tripEmitters.isEmpty()) {
            return;
        }

        //연결 목록을 돌면서 타임라인 변경 이벤트 전송
        for (SseEmitter emitter : tripEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("TIMELINE_UPDATED")
                        .data(Map.of(
                                "message", "새로운 변경 사항이 있습니다.",
                                "changedMemberId", changedMemberId
                        )));
            } catch (IOException e) {
                //전송 중 에러가 발생하면 끊어진 연결로 보고 목록에서 제거
                removeEmitter(tripId, emitter);
                emitter.completeWithError(e);
            }
        }
    }

    //끊어진 SSE 연결을 제거하는 메서드
    private void removeEmitter(Long tripId, SseEmitter emitter) {
        //tripId에 해당하는 SSE 연결 목록 조회
        List<SseEmitter> tripEmitters = emitters.get(tripId);
        //해당 여행 모임의 연결 목록이 없으면 그대로 종료
        if (tripEmitters == null) {
            return;
        }
        //연결 목록에서 끊어진 emitter 제거
        tripEmitters.remove(emitter);
        //연결 목록이 비어 있으면 tripId 자체를 Map에서 제거
        if (tripEmitters.isEmpty()) {
            emitters.remove(tripId);
        }
    }

    //여행 모임 멤버 여부 검증
    private void validateTripMember(Long tripId, Long memberId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from trip_members where trip_id = ? and member_id = ?",
                Integer.class,
                tripId,
                memberId
        );
        boolean isMember = count != null && count > 0;

        if (!isMember) {
            throw new IllegalArgumentException("여행 모임 멤버만 접근할 수 있습니다.");
        }
    }

    //트랜잭션 커밋 성공 후 타임라인 변경 이벤트를 전송
    public void sendTimeLineUpdatedEventAfterCommit(Long tripId, Long changedMemberId) {
        //현재 트랜잭션 동기화가 활성화되어 있으면 커밋 이후 이벤트 전송 예약
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendTimeLineUpdatedEvent(tripId, changedMemberId);
                }
            });
            return;
        }
        //트랜잭션이 없는 상황이면 즉시 이벤트 전송
        sendTimeLineUpdatedEvent(tripId, changedMemberId);
    }

}
