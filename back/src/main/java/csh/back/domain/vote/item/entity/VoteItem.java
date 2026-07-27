package csh.back.domain.vote.item.entity;

import csh.back.domain.trip.place.entity.TripPlace;
import csh.back.domain.vote.vote.entity.Vote;
import csh.back.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

//멤버 엔티티
@Getter
@Entity
@Table(name = "trip_place_vote_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_vote_item",
                columnNames = {"vote_id", "trip_wish_place_id"}  // vote_id 기준
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoteItem extends BaseEntity {

    //FK
    //Join Vote Table
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vote_id",  nullable = false)
    private Vote vote;

    //FK
    //Join TripPlace Table
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_wish_place_id",  nullable = false)
    private TripPlace tripPlace;

    //생성자
    //빌드 사용
    @Builder
    private VoteItem(Vote vote, TripPlace tripPlace) {
        this.vote = vote;
        this.tripPlace = tripPlace;
    }

    public void updateTripPlace(TripPlace tripPlace) {
        this.tripPlace = tripPlace;
    }
}
