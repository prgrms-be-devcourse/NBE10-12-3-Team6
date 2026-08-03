package csh.back.domain.trip.member.service

import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.trip.chat.service.ChatService
import csh.back.domain.trip.event.dto.TripEvent
import csh.back.domain.trip.event.enums.TripEventType
import csh.back.domain.trip.event.service.TripEventService
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.exception.NonMemberException
import csh.back.domain.trip.group.exception.NotFoundException
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.member.dto.response.PastMateResponse
import csh.back.domain.trip.member.dto.response.PastMatesSliceResponse
import csh.back.domain.trip.member.dto.response.TripMemberResponse
import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.member.repository.PastMateProjection
import csh.back.domain.trip.member.repository.TripMemberRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class TripMemberService(
    private val tripGroupRepository: TripGroupRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val memberRepository: MemberRepository,
    private val tripEventService: TripEventService,
    private val chatService: ChatService,
) {

    // 지난 메이트 무한 스크롤 조회 — 최근 함께한 순. keyword가 있으면 이름 부분 매치 필터링.
    // Repository는 dto.response에 의존하지 않도록 PastMateProjection을 반환하고,
    // DTO 변환은 여기서 수행한다.
    @Transactional(readOnly = true)
    fun findPastMates(memberId: Long, keyword: String?, pageable: Pageable): PastMatesSliceResponse {
        val projectionSlice = if (keyword.isNullOrBlank()) {
            tripMemberRepository.findPastMatesByMemberId(memberId, pageable)
        } else {
            tripMemberRepository.searchPastMatesByMemberId(memberId, keyword.trim(), pageable)
        }
        return PastMatesSliceResponse.from(projectionSlice.map(::toPastMateResponse))
    }

    private fun toPastMateResponse(p: PastMateProjection) = PastMateResponse(
        id = p.getId(),
        name = p.getName(),
        travelCount = p.getTravelCount(),
        latestTravelDate = p.getLatestTravelDate(),
        latestGroupName = p.getLatestGroupName(),
    )

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
            chatService.recordSystemMessage(requireNotNull(tripGroup.id), "${member.name}님 여행방 참여")
        }
    }

    // 방장이 지정한 회원들을 여행방 멤버로 추가 (배치).
    // 이미 멤버이거나 존재하지 않는 회원은 조용히 스킵. 새로 추가된 멤버 리스트만 반환.
    fun inviteMembers(
        tripGroupId: Long,
        actorMemberId: Long,
        inviteeMemberIds: List<Long>,
    ): List<TripMemberResponse> {
        val tripGroup: TripGroup = tripGroupRepository.findById(tripGroupId)
            .orElseThrow { NotFoundException("존재하지 않는 모임입니다.") }

        if (!tripMemberRepository.existsByTripGroupIdAndMemberIdAndIsAdminTrue(tripGroupId, actorMemberId)) {
            throw NonMemberException("해당 모임의 소유자가 아닙니다.")
        }

        val uniqueIds = inviteeMemberIds.distinct()
        if (uniqueIds.isEmpty()) return emptyList()

        // 존재하는 회원을 한 번에 조회 (findById N번 → findAllById 1번)
        val memberById = memberRepository.findAllById(uniqueIds).associateBy { it.id!! }

        // 이미 이 방의 멤버인 ID를 한 번에 조회 (exists N번 → IN 쿼리 1번)
        val existingMemberIds = tripMemberRepository
            .findExistingMemberIds(tripGroupId, uniqueIds)
            .toSet()

        // 신규 초대 대상만 엔티티로 준비
        val toInvite = uniqueIds.mapNotNull { inviteeId ->
            if (inviteeId in existingMemberIds) return@mapNotNull null
            val member = memberById[inviteeId] ?: return@mapNotNull null
            TripMember(member = member, tripGroup = tripGroup, isAdmin = false)
        }

        if (toInvite.isEmpty()) return emptyList()

        // 배치 저장 (IDENTITY 전략상 INSERT는 개별로 나가지만 API 흐름은 하나)
        val saved = tripMemberRepository.saveAll(toInvite)

        saved.forEach { tm ->
            tripEventService.publishAfterCommit(
                TripEvent(
                    eventType = TripEventType.TRIP_MEMBER_JOINED,
                    message = "${tm.member.name}님이 여행방에 참여했습니다.",
                    tripGroupId = tripGroupId,
                    actorMemberId = actorMemberId,
                ),
            )
        }

        return saved.map(TripMemberResponse::from)
    }
}
