package csh.back.domain.trip.timeline.dto.response;

import java.util.Map;

public record TimeLineCountResponse(Integer day, Long count) {
    public static TimeLineCountResponse of(Integer day, Long count) {
        return new TimeLineCountResponse(day, count);
    }
}
