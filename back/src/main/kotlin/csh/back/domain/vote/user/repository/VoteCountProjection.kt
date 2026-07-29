package csh.back.domain.vote.user.repository

interface VoteCountProjection {
    fun getVoteItemId(): Long
    fun getVoteCount(): Long
}