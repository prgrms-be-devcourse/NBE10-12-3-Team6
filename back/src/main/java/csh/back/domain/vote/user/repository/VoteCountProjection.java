package csh.back.domain.vote.user.repository;

public interface VoteCountProjection {
    Long getVoteItemId();
    Long getVoteCount();
}
