package csh.back.domain.vote.vote.dto.response;

import java.time.LocalDate;
import java.util.List;

public record VoteFindListResponse(
        LocalDate date,
        List<VoteWithTimeLineResponse> timeLines
) {
        public static VoteFindListResponse of(LocalDate date, List<VoteWithTimeLineResponse> timeLines) {
            return new VoteFindListResponse(date, timeLines);
        }
}
