package csh.back.domain.trip.member.validator

import csh.back.domain.trip.member.repository.TripMemberRepository
import org.springframework.stereotype.Service

@Service
class TripMemberValidator(
    private val tripMemberRepository: TripMemberRepository,
) {
    fun validMember(tripId: Long, memberId: Long) {
        if (!tripMemberRepository.existsByTripGroupIdAndMemberId(tripId, memberId)) {
            throw RuntimeException()
        }
    }
}
