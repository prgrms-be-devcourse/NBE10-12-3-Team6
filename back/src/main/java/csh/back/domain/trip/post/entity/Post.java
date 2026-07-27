package csh.back.domain.trip.post.entity;

import csh.back.domain.member.entity.Member;
import csh.back.domain.trip.group.entity.TripGroup;
import csh.back.domain.trip.member.entity.TripMember;
import csh.back.domain.trip.timeline.entity.TimeLine;
import csh.back.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "posts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseEntity {

    //FK
    //Join TripMember Table
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private TripMember author;

    //FK
    //Join TripTimeline Table
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timeline_id")
    private TimeLine timeLine;

    //영상인지 이미지인지 논리판단
    private Boolean isImg;
    //불러오는 이미지 URL
    private String contentUrl;
    //포스트 글 내용
    @Column(length = 1000)
    private String content;
    //위치값
    private String location;

    @Builder
    private Post(
            TripMember author,
            TimeLine timeLine,
            Boolean isImg,
            String content,
            String location,
            String contentUrl
    ) {
        this.author = author;
        this.timeLine = timeLine;
        this.isImg = isImg;
        this.content = content;
        this.location = location;
        this.contentUrl = contentUrl;
    }
    public void update(String content, String location) {
        this.content = content;
        this.location = location;
    }
}
