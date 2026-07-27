package csh.back.domain.vote.vote.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VoteStatus {
    CONFIRMED("투표 확정"),
    PENDING("투표 진행중"),
    EXPIRED("투표 기한 만료");

    private final String nickname ;
}