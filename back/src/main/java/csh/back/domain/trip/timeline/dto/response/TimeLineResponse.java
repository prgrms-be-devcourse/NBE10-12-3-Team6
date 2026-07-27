package csh.back.domain.trip.timeline.dto.response;

import csh.back.domain.trip.timeline.entity.TimeLine;

import java.time.LocalDateTime;

public record TimeLineResponse(
        Long timelineId,
        Integer dayNumber,
        LocalDateTime startTime,
        LocalDateTime endTime
) {
    //서비스 로직에서 조회 결과를 응답으로 바꿀 때 편하게 하기 위한 정적 메서드
    public static TimeLineResponse from(TimeLine timeLine) {
        return new TimeLineResponse(
                timeLine.getId(),
                timeLine.getDayNumber(),
                timeLine.getStartTime(),
                timeLine.getEndTime()
        );
    }
}
