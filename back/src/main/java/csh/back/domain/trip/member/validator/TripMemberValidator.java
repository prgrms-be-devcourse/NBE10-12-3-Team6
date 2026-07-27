package csh.back.domain.trip.member.validator;

import csh.back.domain.trip.member.repository.TripMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class TripMemberValidator {
    private final TripMemberRepository tripMemberRepository;

    public void validMember(Long tripId, Long memberId) {
        if(!tripMemberRepository.existsByTripGroupIdAndMemberId(tripId, memberId)) {
            throw new RuntimeException();
        }
    }
}
