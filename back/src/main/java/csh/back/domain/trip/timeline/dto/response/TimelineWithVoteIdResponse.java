package csh.back.domain.trip.timeline.dto.response;

import csh.back.domain.trip.timeline.entity.Timeline;

import java.time.LocalDateTime;

public record TimelineWithVoteIdResponse(
        Long timelineId,
        Long dayNumber,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String confirmedPlaceName,
        String category,
        Long voteId
) {
    //서비스 로직에서 조회 결과를 응답으로 바꿀 때 편하게 하기 위한 정적 메서드
    public static TimelineWithVoteIdResponse of(Timeline timeline, Long voteId) {
        String confirmedPlaceName = timeline.getTripWishPlace() != null ? timeline.getTripWishPlace().getName() : null;
        String category = timeline.getTripWishPlace() != null ? timeline.getTripWishPlace().getCategory() : null;
        return new TimelineWithVoteIdResponse(
                timeline.getId(),
                timeline.getDayNumber(),
                timeline.getStartTime(),
                timeline.getEndTime(),
                confirmedPlaceName,
                category,
                voteId
        );
    }
}
