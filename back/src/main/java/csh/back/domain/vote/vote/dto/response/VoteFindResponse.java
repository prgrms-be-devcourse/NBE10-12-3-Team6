package csh.back.domain.vote.vote.dto.response;

public record VoteFindResponse(
        Long tripPlaceId,
        String place,
        Long count,
        boolean isVoted) {
    public static VoteFindResponse of(Long tripPlaceId, String place, Long count, boolean isVoted) {
        return new VoteFindResponse(tripPlaceId, place, count, isVoted);
    }
}
