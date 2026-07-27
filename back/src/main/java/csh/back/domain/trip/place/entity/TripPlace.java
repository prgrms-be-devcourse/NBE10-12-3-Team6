package csh.back.domain.trip.place.entity;

import csh.back.domain.trip.group.entity.TripGroup;
import csh.back.domain.trip.member.entity.TripMember;
import csh.back.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "trip_wish_places",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wish_place",
                columnNames = {"kakao_place_id", "trip_id"}
        )
)
public class TripPlace extends BaseEntity {

    //FK
    //Join tripGroup Table
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripGroup tripGroup;

    //장소 이름
    private String name;

    //테마
    private String theme;

    //주소
    private String address;

    //카카오 플레이스 id
    private String kakaoPlaceId;

    //카카오멥 URL
    private String kakaoMapUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private TripMember createdBy;

    //생성자
    //빌드 사용
    @Builder
    private TripPlace(TripGroup tripGroup, String name, String theme, String address, String kakaoPlaceId, String kakaoMapUrl, TripMember createdBy) {
        this.tripGroup = tripGroup;
        this.name = name;
        this.theme = theme;
        this.address = address;
        this.kakaoPlaceId = kakaoPlaceId;
        this.kakaoMapUrl = kakaoMapUrl;
        this.createdBy = createdBy;
    }
}
