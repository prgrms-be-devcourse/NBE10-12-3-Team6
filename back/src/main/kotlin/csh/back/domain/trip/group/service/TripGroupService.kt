package csh.back.domain.trip.group.service

import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.trip.group.dto.request.TripGroupModifyRequest
import csh.back.domain.trip.group.dto.request.TripGroupRequest
import csh.back.domain.trip.group.dto.response.TripGroupDetailResponse
import csh.back.domain.trip.group.dto.response.TripGroupResponse
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.exception.NonMemberException
import csh.back.domain.trip.group.exception.NotFoundException
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.member.dto.response.TripMemberResponse
import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.member.repository.TripMemberRepository
import org.apache.commons.lang3.RandomStringUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional
class TripGroupService(
    private val tripGroupRepository: TripGroupRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val memberRepository: MemberRepository,
) {

    @Transactional(readOnly = true)
    fun getGroups(ownerId: Long, keyword: String?, startDate: String?): List<TripGroupResponse> =
        tripGroupRepository.findAllByMemberIdWithSearch(ownerId, keyword, startDate)
            .map { TripGroupResponse.from(it) }

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
}
