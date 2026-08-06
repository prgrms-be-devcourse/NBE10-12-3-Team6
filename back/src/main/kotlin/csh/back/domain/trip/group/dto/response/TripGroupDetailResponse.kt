package csh.back.domain.trip.group.dto.response

import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.member.dto.response.TripMemberResponse
import java.time.LocalDate

data class TripGroupDetailResponse(
    val id: Long,
    val name: String,
    val ownerId: Long,
    val region: String,
    val joinCode: String,
    val nights: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val members: List<TripMemberResponse>,
) {
    companion object {
        @JvmStatic
        fun from(tripGroup: TripGroup, members: List<TripMemberResponse>): TripGroupDetailResponse =
            TripGroupDetailResponse(
                id = requireNotNull(tripGroup.id),
                name = tripGroup.name,
                ownerId = requireNotNull(tripGroup.owner.id),
                region = tripGroup.region,
                joinCode = tripGroup.joinCode,
                nights = tripGroup.nights,
                startDate = tripGroup.startDate,
                endDate = tripGroup.endDate,
                members = members,
            )
    }
}
