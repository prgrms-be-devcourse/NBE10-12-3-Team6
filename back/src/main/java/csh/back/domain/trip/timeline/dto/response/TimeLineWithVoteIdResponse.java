package csh.back.domain.trip.timeline.dto.response;

import csh.back.domain.trip.place.entity.TripPlace;
import csh.back.domain.trip.timeline.entity.TimeLine;

import java.time.LocalDateTime;

public record TimeLineWithVoteIdResponse(
        Long timelineId,
        Integer dayNumber,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String confirmedPlaceName,
        String category,
        Long voteId
) {
    //서비스 로직에서 조회 결과를 응답으로 바꿀 때 편하게 하기 위한 정적 메서드
    public static TimeLineWithVoteIdResponse of(TimeLine timeLine, Long voteId) {
        String confirmedPlaceName = timeLine.getConfirmedPlace() != null ? timeLine.getConfirmedPlace().getName() : null;
        String category = timeLine.getConfirmedPlace() != null ? timeLine.getConfirmedPlace().getTheme() : null;
        return new TimeLineWithVoteIdResponse(
                timeLine.getId(),
                timeLine.getDayNumber(),
                timeLine.getStartTime(),
                timeLine.getEndTime(),
                confirmedPlaceName,
                category,
                voteId
        );
    }
}
