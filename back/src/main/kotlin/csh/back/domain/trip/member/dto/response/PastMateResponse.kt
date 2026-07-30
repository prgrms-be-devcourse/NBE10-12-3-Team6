package csh.back.domain.trip.member.dto.response

import java.time.LocalDate

data class PastMateResponse(
    val id: Long,
    val name: String,
    val email: String,
    val travelCount: Long,
    val latestTravelDate: LocalDate,
)
