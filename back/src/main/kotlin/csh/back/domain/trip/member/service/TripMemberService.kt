package csh.back.domain.trip.member.service

import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.trip.event.dto.TripEvent
import csh.back.domain.trip.event.enums.TripEventType
import csh.back.domain.trip.event.service.TripEventService
import csh.back.domain.trip.group.exception.NotFoundException
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.member.repository.TripMemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class TripMemberService(
    private val tripGroupRepository: TripGroupRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val memberRepository: MemberRepository,
    private val tripEventService: TripEventService,
) {

    fun createJoinMember(joinCode: String, memberId: Long) {
        tripGroupRepository.findByJoinCode(joinCode).ifPresent { tripGroup ->
            val member = memberRepository.findById(memberId)
                .orElseThrow { NotFoundException("존재하지 않는 유저") }

            if (tripMemberRepository.existsByTripGroupIdAndMemberId(tripGroup.id!!, member.id!!)) {
                return@ifPresent
            }

            tripMemberRepository.save(
                TripMember(
                    member = member,
                    tripGroup = tripGroup,
                    isAdmin = false,
                ),
            )
            tripEventService.publishAfterCommit(
                TripEvent(
                    eventType = TripEventType.TRIP_MEMBER_JOINED,
                    message = "${member.name}님 여행방 참여",
                    tripGroupId = requireNotNull(tripGroup.id),
                    actorMemberId = memberId,
                ),
            )
        }
    }
}
