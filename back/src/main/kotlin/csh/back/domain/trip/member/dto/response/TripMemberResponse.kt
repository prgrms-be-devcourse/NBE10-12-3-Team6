package csh.back.domain.trip.member.dto.response

import csh.back.domain.trip.member.entity.TripMember

data class TripMemberResponse(
    val memberId: Long,
    val name: String,
    val admin: Boolean,
) {
    companion object {
        @JvmStatic
        fun from(tripMember: TripMember): TripMemberResponse = TripMemberResponse(
            memberId = requireNotNull(tripMember.member.id),
            name = tripMember.member.name,
            admin = tripMember.isAdmin,
        )
    }
}
