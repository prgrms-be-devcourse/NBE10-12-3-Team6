package csh.back.domain.trip.post.dto.response;

import java.util.List;

public record TimelinePostResponse(

        Long timelineId,

        List<PostResponse> posts

) {}
