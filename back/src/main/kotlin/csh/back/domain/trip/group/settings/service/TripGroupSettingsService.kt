package csh.back.domain.trip.group.settings.service

import csh.back.domain.trip.event.dto.TripEvent
import csh.back.domain.trip.event.enums.TripEventType
import csh.back.domain.trip.event.service.TripEventService
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.exception.NonMemberException
import csh.back.domain.trip.group.exception.NotFoundException
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.group.settings.dto.request.TripGroupSettingsUpdateRequest
import csh.back.domain.trip.group.settings.dto.response.TripGroupSettingsResponse
import csh.back.domain.trip.group.settings.entity.TripGroupSettings
import csh.back.domain.trip.group.settings.exception.TripGroupSettingsLockedException
import csh.back.domain.trip.chat.service.ChatService
import csh.back.domain.trip.group.settings.repository.TripGroupSettingsRepository
import csh.back.domain.trip.member.repository.TripMemberRepository
import csh.back.domain.vote.vote.enums.VoteStatus
import csh.back.domain.vote.vote.repository.VoteRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId

@Service
@Transactional
class TripGroupSettingsService(
    private val tripGroupRepository: TripGroupRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val tripGroupSettingsRepository: TripGroupSettingsRepository,
    private val tripEventService: TripEventService,
    private val voteRepository: VoteRepository,
    private val chatService: ChatService,
) {

    fun getSettings(
        tripGroupId: Long,
        memberId: Long,
    ): TripGroupSettingsResponse {
        val tripGroup = findTripGroup(tripGroupId)
        validateMember(tripGroupId, memberId)
        val settings = findOrCreateSettings(tripGroup)

        return TripGroupSettingsResponse.from(
            settings = settings,
            editable = tripGroup.owner.id == memberId && isBeforeTripStart(tripGroup),
        )
    }

    fun updateFreeTimeMinutes(
        tripGroupId: Long,
        dayNumber: Int,
        memberId: Long,
        request: TripGroupSettingsUpdateRequest,
    ): TripGroupSettingsResponse {
        val tripGroup = findTripGroup(tripGroupId)
        validateOwner(tripGroup, memberId)
        validateBeforeTripStart(tripGroup)
        validateDayNumber(tripGroup, dayNumber)
        val settings = findOrCreateSettings(tripGroup)
        settings.updateFreeTimeMinutes(
            dayNumber = dayNumber,
            freeTimeMinutes = request.freeTimeMinutesOrThrow(),
        )

        tripEventService.publishAfterCommit(
            TripEvent(
                eventType = TripEventType.FREE_TIME_RANGE_UPDATED,
                message = "${dayNumber}일차 자유시간 범위 변경",
                tripGroupId = tripGroupId,
                actorMemberId = memberId,
                dayNumber = dayNumber.toLong(),
            ),
        )
        chatService.recordSystemMessage(tripGroupId, "${dayNumber}일차 자유시간 범위 변경")

        return TripGroupSettingsResponse.from(
            settings = settings,
            editable = true,
        )
    }

    fun updateAllFreeTimeMinutes(
        tripGroupId: Long,
        memberId: Long,
        request: TripGroupSettingsUpdateRequest,
    ): TripGroupSettingsResponse {
        val tripGroup = findTripGroup(tripGroupId)
        validateOwner(tripGroup, memberId)
        validateBeforeTripStart(tripGroup)
        val settings = findOrCreateSettings(tripGroup)
        settings.updateAllFreeTimeMinutes(
            totalDays = tripGroup.nights + 1,
            freeTimeMinutes = request.freeTimeMinutesOrThrow(),
        )

        tripEventService.publishAfterCommit(
            TripEvent(
                eventType = TripEventType.FREE_TIME_RANGE_UPDATED,
                message = "모든 일차 자유시간 범위 변경",
                tripGroupId = tripGroupId,
                actorMemberId = memberId,
            ),
        )
        chatService.recordSystemMessage(tripGroupId, "모든 일차 자유시간 범위 변경")

        return TripGroupSettingsResponse.from(
            settings = settings,
            editable = true,
        )
    }

    fun updateAnonymousVote(
        tripGroupId: Long,
        memberId: Long,
        isAnonymousVote: Boolean,
    ): TripGroupSettingsResponse {
        validateTripAdmin(tripGroupId, memberId)
        val tripGroup = findTripGroup(tripGroupId)
        validateBeforeTripStart(tripGroup, "여행 시작일부터 익명 투표 설정을 변경할 수 없습니다.")
        val settings = findOrCreateSettings(tripGroup)
        settings.isAnonymousVote = isAnonymousVote
        voteRepository.updateIsAnonymousByTripGroupIdAndStatus(tripGroupId, isAnonymousVote, VoteStatus.PENDING)

        return TripGroupSettingsResponse.from(
            settings = settings,
            editable = tripGroup.owner.id == memberId && isBeforeTripStart(tripGroup),
        )
    }

    private fun findTripGroup(tripGroupId: Long): TripGroup =
        tripGroupRepository.findById(tripGroupId)
            .orElseThrow {
                NotFoundException("존재하지 않는 모임입니다.")
            }

    private fun validateMember(
        tripGroupId: Long,
        memberId: Long,
    ) {
        if (!tripMemberRepository.existsByTripGroupIdAndMemberId(tripGroupId, memberId)) {
            throw NonMemberException("해당 모임의 멤버가 아닙니다.")
        }
    }

    private fun validateOwner(
        tripGroup: TripGroup,
        memberId: Long,
    ) {
        if (tripGroup.owner.id != memberId) {
            throw NonMemberException("해당 모임의 소유자만 설정을 변경할 수 있습니다.")
        }
    }

    private fun validateTripAdmin(tripGroupId: Long, memberId: Long) {
        val isAdmin = tripMemberRepository.existsByTripGroupIdAndMemberIdAndIsAdminTrue(tripGroupId, memberId)
        require(isAdmin) { "여행 모임 방장만 접근할 수 있습니다." }
    }

    private fun validateDayNumber(
        tripGroup: TripGroup,
        dayNumber: Int,
    ) {
        if (dayNumber !in 1..(tripGroup.nights + 1)) {
            throw NotFoundException("존재하지 않는 여행 일차입니다.")
        }
    }

    private fun validateBeforeTripStart(
        tripGroup: TripGroup,
        message: String = "여행 시작일부터 자유시간 범위를 변경할 수 없습니다.",
    ) {
        if (!isBeforeTripStart(tripGroup)) {
            throw TripGroupSettingsLockedException(message)
        }
    }

    private fun isBeforeTripStart(tripGroup: TripGroup): Boolean =
        LocalDate.now(SEOUL_ZONE_ID).isBefore(tripGroup.startDate)

    private fun findOrCreateSettings(tripGroup: TripGroup): TripGroupSettings =
        tripGroupSettingsRepository.findByTripGroupId(requireNotNull(tripGroup.id))
            .orElseGet {
                tripGroupSettingsRepository.save(
                    TripGroupSettings(tripGroup = tripGroup),
                )
            }

    private companion object {
        val SEOUL_ZONE_ID: ZoneId = ZoneId.of("Asia/Seoul")
    }
}