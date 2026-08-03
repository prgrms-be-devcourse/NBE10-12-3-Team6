package csh.back.domain.trip.member.dto.request

import jakarta.validation.constraints.NotEmpty

data class TripMemberInviteRequest(
    @field:NotEmpty(message = "초대할 회원 ID를 하나 이상 지정해야 합니다.")
    val memberIds: List<Long>,
)
