package csh.back.domain.vote.user.repository;

import csh.back.domain.vote.user.entity.VoteUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VoteUserRepository extends JpaRepository<VoteUser, Long> {
    @Query("SELECT vi.id AS voteItemId, COUNT(vu) AS voteCount FROM VoteUser vu JOIN vu.voteItem vi WHERE vi.vote.id = :voteId GROUP BY vi.id")
    List<VoteCountProjection> countGroupByVoteId(Long voteId);

    List<VoteUser> findByVoteItemId(Long voteItemId);

    Optional<VoteUser> findByVoteIdAndTripMemberId(Long voteId, Long tripMemberId);
}
