package csh.back.domain.trip.timeline.dto.response;

public record TimelineCountResponse(Integer day, Long count) {
    public static TimelineCountResponse of(Integer day, Long count) {
        return new TimelineCountResponse(day, count);
    }
}
