package csh.back.domain.vote.vote.repository;

import csh.back.domain.trip.timeline.entity.TimeLine;
import csh.back.domain.vote.vote.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    @Query("SELECT v FROM Vote v JOIN FETCH v.timeLine tl WHERE tl.tripGroup.id = :tripId")
    List<Vote> findVotesWithTimeLineByTripGroupId(Long tripId);

    @Query("SELECT v.timeLine.id AS timeLineId, v.id AS voteId FROM Vote v WHERE v.timeLine.id IN :timeLineIds")
    List<VoteTimeLineIdProjection> findVoteIdsByTimeLineIds(List<Long> timeLineIds);

    @Query("SELECT v.timeLine FROM Vote v WHERE v.id = :voteId")
    Optional<TimeLine> findTimeLineByVoteId(Long voteId);

}
