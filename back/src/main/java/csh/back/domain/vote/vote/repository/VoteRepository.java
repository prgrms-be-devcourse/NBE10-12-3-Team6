package csh.back.domain.vote.vote.repository;

import csh.back.domain.trip.timeline.entity.Timeline;
import csh.back.domain.vote.vote.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    @Query("SELECT v FROM Vote v JOIN FETCH v.timeline tl WHERE tl.tripGroup.id = :tripId")
    List<Vote> findVotesWithTimelineByTripGroupId(Long tripId);

    @Query("SELECT v.timeline.id AS timelineId, v.id AS voteId FROM Vote v WHERE v.timeline.id IN :timeLineIds")
    List<VoteTimelineIdProjection> findVoteIdsByTimeLineIds(List<Long> timeLineIds);

    @Query("SELECT v.timeline FROM Vote v WHERE v.id = :voteId")
    Optional<Timeline> findTimeLineByVoteId(Long voteId);

}
