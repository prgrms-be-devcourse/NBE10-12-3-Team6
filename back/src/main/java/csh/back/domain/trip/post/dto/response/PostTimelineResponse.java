package csh.back.domain.trip.post.dto.response;

import csh.back.domain.trip.post.entity.Post;

import java.time.LocalDateTime;

public record PostTimelineResponse(
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long timeLineId,      // ← 추가: 일정 슬롯이면 값, 빈 칸이면 null
        String confirmedPlaceName,
        boolean isTaken

) {
    public static PostTimelineResponse of(
            LocalDateTime startTime,
            LocalDateTime endTime,
            Long timeLineId,      // ← 추가: 일정 슬롯이면 값, 빈 칸이면 null
            String confirmedPlaceName,
            boolean isTaken) {
        return new PostTimelineResponse(startTime, endTime, timeLineId, confirmedPlaceName, isTaken);
    }
}
