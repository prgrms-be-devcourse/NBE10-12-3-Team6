package csh.back.domain.vote.user.entity;

import csh.back.domain.trip.member.entity.TripMember;
import csh.back.domain.trip.timeline.entity.TimeLine;
import csh.back.domain.vote.item.entity.VoteItem;
import csh.back.domain.vote.vote.entity.Vote;
import csh.back.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "trip_vote_users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_vote_user",
                columnNames = {"vote_id", "trip_member_id"}  // vote_id 기준
        )
)

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoteUser extends BaseEntity {

    //FK
    //Join Vote Table
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vote_id",  nullable = false)
    private Vote vote;

    //FK
    //Join VoteItem Table
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vote_item_id",  nullable = false)
    private VoteItem voteItem;

    //FK
    //Join TripMember Table
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_member_id",  nullable = false)
    private TripMember tripMember;

    @Column(nullable = false)
    private Integer updateCount;

    //생성자
    //빌드 사용
    @Builder
    private VoteUser(Vote vote, VoteItem voteItem, TripMember tripMember, Integer updateCount) {
        this.vote = vote;
        this.voteItem = voteItem;
        this.tripMember = tripMember;
        this.updateCount = updateCount;
    }

    public void updateVoteItemAndincreaseUpdateCount(VoteItem voteItem) {
        this.voteItem = voteItem;
        this.updateCount++;
    }
}
