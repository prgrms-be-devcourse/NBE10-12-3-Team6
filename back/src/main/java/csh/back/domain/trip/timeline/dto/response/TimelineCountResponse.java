package csh.back.domain.trip.timeline.dto.response;

public record TimelineCountResponse(Long day, Long count) {
    public static TimelineCountResponse of(Long day, Long count) {
        return new TimelineCountResponse(day, count);
    }
}
