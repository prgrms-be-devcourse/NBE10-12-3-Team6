package csh.back.domain.vote.vote.dto.response;

public record VoteFindResponse(
        Long placeId,
        String place,
        Long count,
        boolean isVoted) {
    public static VoteFindResponse of(Long placeId, String place, Long count, boolean isVoted) {
        return new VoteFindResponse(placeId, place, count, isVoted);
    }
}
