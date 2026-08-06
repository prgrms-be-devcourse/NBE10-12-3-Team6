package csh.back.domain.vote.vote.enums

enum class VoteStatus(val nickname: String) {
    CONFIRMED("투표 확정"),
    PENDING("투표 진행중"),
    EXPIRED("투표 기한 만료"),
}
