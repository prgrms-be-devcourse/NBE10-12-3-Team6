package csh.back.domain.vote.vote.dto.response

import java.time.LocalDate

data class VoteFindListResponse(
    val date: LocalDate,
    val timeLines: List<VoteWithTimelineResponse>,
) {
    companion object {
        @JvmStatic
        fun of(date: LocalDate, timeLines: List<VoteWithTimelineResponse>): VoteFindListResponse =
            VoteFindListResponse(date, timeLines)
    }
}
