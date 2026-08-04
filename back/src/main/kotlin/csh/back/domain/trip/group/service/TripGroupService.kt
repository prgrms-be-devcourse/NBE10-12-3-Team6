package csh.back.domain.trip.group.service

import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.trip.chat.service.ChatService
import csh.back.domain.trip.event.dto.TripEvent
import csh.back.domain.trip.event.enums.TripEventType
import csh.back.domain.trip.event.service.TripEventService
import csh.back.domain.trip.group.dto.request.TripGroupModifyRequest
import csh.back.domain.trip.group.dto.request.TripGroupRequest
import csh.back.domain.trip.group.dto.response.TripGroupDetailResponse
import csh.back.domain.trip.group.dto.response.TripGroupResponse
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.exception.NonMemberException
import csh.back.domain.trip.group.exception.NotFoundException
import csh.back.domain.trip.group.exception.TripDeletionNotAllowedException
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.group.settings.entity.TripGroupSettings
import csh.back.domain.trip.group.settings.repository.TripGroupSettingsRepository
import csh.back.domain.trip.member.dto.response.TripMemberResponse
import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.member.repository.TripMemberRepository
import csh.back.global.dto.SliceResponse
import org.apache.commons.lang3.RandomStringUtils
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional
class TripGroupService(
    private val tripGroupRepository: TripGroupRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val memberRepository: MemberRepository,
    private val tripEventService: TripEventService,
    private val tripGroupSettingsRepository: TripGroupSettingsRepository,
    private val chatService: ChatService,
) {

    @Transactional(readOnly = true)
    fun getGroups(
        ownerId: Long,
        keyword: String?,
        startDate: String?,
        pageable: Pageable,
    ): SliceResponse<TripGroupResponse> =
        // Repository가 Projections.constructor로 이미 TripGroupResponse를 만들어 돌려주므로
        // 여기서 별도 map 변환이 필요 없다. (N+1 방지 겸 매핑 로직 중복 제거)
        SliceResponse.from(
            tripGroupRepository.findAllByMemberIdWithSearch(ownerId, keyword, startDate, pageable),
        )

    fun writeGroup(request: TripGroupRequest, ownerId: Long): TripGroupResponse {
        val nights = request.nightsOrThrow()
        val startDate = LocalDate.parse(request.startDate)
        val endDate = startDate.plusDays(nights.toLong())

        val owner = memberRepository.findById(ownerId)
            .orElseThrow { NotFoundException("존재하지 않는 유저") }

        val group = TripGroup(
            owner = owner,
            name = request.name,
            region = request.region,
            nights = nights,
            joinCode = createJoinCode(),
            startDate = startDate,
            endDate = endDate,
        )
        val savedGroup = tripGroupRepository.save(group)
        tripGroupSettingsRepository.save(
            TripGroupSettings(tripGroup = savedGroup),
        )

        tripMemberRepository.save(
            TripMember(
                member = owner,
                tripGroup = savedGroup,
                isAdmin = true,
            ),
        )

        return TripGroupResponse.from(savedGroup)
    }

    @Transactional(readOnly = true)
    fun getGroupDetail(tripId: Long, ownerId: Long): TripGroupDetailResponse {
        val group = tripGroupRepository.findById(tripId)
            .orElseThrow { NotFoundException("존재하지 않는 모임입니다.") }

        if (!tripMemberRepository.existsByTripGroupIdAndMemberId(tripId, ownerId)) {
            throw NonMemberException("해당 모임의 멤버가 아닙니다.")
        }

        val members = tripMemberRepository.findByTripGroupId(tripId)
            .map { TripMemberResponse.from(it) }

        return TripGroupDetailResponse.from(group, members)
    }

    fun modifyGroupDetail(tripId: Long, ownerId: Long, request: TripGroupModifyRequest): TripGroupResponse {
        val group = tripGroupRepository.findById(tripId)
            .orElseThrow { NotFoundException("존재하지 않는 모임입니다.") }

        if (!group.owner.id!!.equals(ownerId)) {
            throw NonMemberException("해당 모임의 소유자가 아닙니다.")
        }

        group.modify(request)
        tripEventService.publishAfterCommit(
            TripEvent(
                eventType = TripEventType.TRIP_GROUP_UPDATED,
                message = "여행방 정보 변경",
                tripGroupId = tripId,
                actorMemberId = ownerId,
            ),
        )
        chatService.recordSystemMessage(tripId, "여행방 정보 변경")
        return TripGroupResponse.from(group)
    }

    fun createJoinCode(): String {
        var joinCode: String
        do {
            joinCode = RandomStringUtils.random(7, true, true)
        } while (tripGroupRepository.existsByJoinCode(joinCode))
        return joinCode
    }

    fun findTripGroupById(tripId: Long): TripGroup =
        tripGroupRepository.findById(tripId).orElseThrow { RuntimeException() }

    companion object {
        // 정책: 여행 시작 최소 1일 전까지 삭제 허용(전날까지). 당일/진행중/완료된 방은 삭제 불가.
        // 프론트 UI의 "삭제 가능 리스트" 필터 기준(daysUntilStart >= 1)과 반드시 일치시켜야 함.
        const val DELETE_ALLOWED_DAYS_BEFORE_START: Long = 1
    }

    // 단건 삭제. 다중 삭제(deleteGroups)에서 각 방 처리에도 재사용.
    // @SQLDelete가 발동해 실제 DELETE 대신 deleted_at을 채운다.
    fun deleteGroup(tripId: Long, memberId: Long) {
        val group = tripGroupRepository.findById(tripId)
            .orElseThrow { NotFoundException("존재하지 않는 모임입니다.") }

        if (group.owner.id != memberId) {
            throw NonMemberException("해당 모임의 소유자가 아닙니다.")
        }

        val daysUntilStart = LocalDate.now().until(group.startDate, java.time.temporal.ChronoUnit.DAYS)
        if (daysUntilStart < DELETE_ALLOWED_DAYS_BEFORE_START) {
            throw TripDeletionNotAllowedException(
                "여행 시작 전날까지만 삭제할 수 있어요.",
            )
        }

        tripGroupRepository.delete(group)
    }

    // 다중 삭제. all-or-nothing: 하나라도 정책 위반이면 @Transactional이 롤백해 이미 지운 방도 되돌림.
    // 부분 성공을 허용하지 않는 이유: 프론트가 이미 "삭제 가능 리스트"만 노출해 왔으므로
    // 이 API가 실패한다면 서버 시각/데이터 정합성 문제일 가능성이 크고, 그런 상황에서 일부만 지우면
    // 사용자에게 혼란만 줌.
    fun deleteGroups(tripIds: List<Long>, memberId: Long): Int {
        val uniqueIds = tripIds.distinct()
        if (uniqueIds.isEmpty()) return 0
        uniqueIds.forEach { deleteGroup(it, memberId) }
        return uniqueIds.size
    }
}
