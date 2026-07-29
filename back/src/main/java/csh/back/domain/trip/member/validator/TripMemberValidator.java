package csh.back.domain.trip.member.validator;

import csh.back.domain.trip.member.repository.TripMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class TripMemberValidator {
    private final TripMemberRepository tripMemberRepository;

    public void validMember(Long tripGroupId, Long memberId) {
        if(!tripMemberRepository.existsByTripGroupIdAndMemberId(tripGroupId, memberId)) {
            throw new RuntimeException();
        }
    }
}
