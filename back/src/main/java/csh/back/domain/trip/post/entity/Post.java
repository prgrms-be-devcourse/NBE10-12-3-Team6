package csh.back.domain.trip.post.entity;

import csh.back.domain.trip.member.entity.TripMember;
import csh.back.domain.trip.timeline.entity.Timeline;
import csh.back.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "trip_posts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_member_id", nullable = false)
    private TripMember author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_timeline_id")
    private Timeline timeline;

    private String type;

    private String contentUrl;

    @Column(length = 1000)
    private String content;

    @Builder
    private Post(TripMember author, Timeline timeline, String type, String contentUrl, String content) {
        this.author = author;
        this.timeline = timeline;
        this.type = type;
        this.contentUrl = contentUrl;
        this.content = content;
    }

    public void update(String content) {
        this.content = content;
    }
}