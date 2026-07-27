package csh.back.domain.trip.post.dto.response;

import java.util.List;

public record TimelinePostsResponse(

        Long timelineId,

        int dayNumber,

        List<PostResponse> posts

) {}