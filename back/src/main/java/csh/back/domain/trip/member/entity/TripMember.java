package csh.back.domain.trip.member.entity;

import csh.back.domain.member.entity.Member;
import csh.back.domain.trip.group.entity.TripGroup;
import csh.back.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

//여행방 멤버
@Getter
@Entity
@Table(name = "trip_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_trip_member",
                columnNames = {"trip_group_id", "member_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripMember extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name ="member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name ="trip_group_id", nullable = false)
    private TripGroup tripGroup;

    //방장인지 아닌지
    private boolean isAdmin;

    //생성자
    //빌드 사용
    @Builder
    private TripMember(Member member, TripGroup tripGroup, boolean isAdmin) {
        this.member = member;
        this.tripGroup = tripGroup;
        this.isAdmin = isAdmin;
    }
}
