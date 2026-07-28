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
            Long timeLineId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String confirmedPlaceName,
            LocalDateTime createdAt
    ) {
        public static PostSummary from(Post post) {
            Long timeLineId = post.getTimeline() != null ? post.getTimeline().getId() : null;
            LocalDateTime startTime = timeLineId != null ? post.getTimeline().getStartTime() : null;
            LocalDateTime endTime = timeLineId != null ? post.getTimeline().getEndTime() : null;
            Timeline timeLine = post.getTimeline();
            String confirmedPlaceName = timeLine != null && timeLine.getConfirmedPlace() != null ? post.getTimeline().getConfirmedPlace().getName() : null;

            return new PostSummary(
                    post.getId(),
                    post.getContentUrl(),
                    timeLineId,
                    startTime,
                    endTime,
                    confirmedPlaceName,
                    post.getCreatedAt()
            );
        }


    }
}
