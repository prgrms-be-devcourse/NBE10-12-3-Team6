package csh.back.domain.vote.vote.repository

interface VoteTimelineIdProjection {
    fun getVoteId(): Long
    fun getTimelineId(): Long
}
