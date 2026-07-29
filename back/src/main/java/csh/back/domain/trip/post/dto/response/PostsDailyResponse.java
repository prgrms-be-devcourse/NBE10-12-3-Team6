package csh.back.domain.trip.post.dto.response;

import csh.back.domain.trip.post.entity.Post;
import csh.back.domain.trip.timeline.entity.Timeline;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

public record PostsDailyResponse(
        LocalDate date,
        List<PostSummary> posts
) {
    public record PostSummary(
            Long postId,
            @Schema(
                    description = "이미지 URL",
                    example = "https://example.com/images/post1.jpg",
                    nullable = true
            )
            String contentUrl,
            Long timelineId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String confirmedPlaceName,
            LocalDateTime createdAt
    ) {
        public static PostSummary from(Post post) {
            Long timelineId = post.getTimeline() != null ? post.getTimeline().getId() : null;
            LocalDateTime startTime = timelineId != null ? post.getTimeline().getStartTime() : null;
            LocalDateTime endTime = timelineId != null ? post.getTimeline().getEndTime() : null;
            Timeline timeline = post.getTimeline();
            String confirmedPlaceName = timeline != null && timeline.getTripWishPlace() != null ? post.getTimeline().getTripWishPlace().getName() : null;

            return new PostSummary(
                    post.getId(),
                    post.getContentUrl(),
                    timelineId,
                    startTime,
                    endTime,
                    confirmedPlaceName,
                    post.getCreatedAt()
            );
        }


    }
}
