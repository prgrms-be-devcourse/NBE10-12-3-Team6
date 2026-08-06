package csh.back.domain.trip.member.dto.response

import java.time.LocalDate

data class PastMateResponse(
    val id: Long,
    val name: String,
    val travelCount: Long,
    val latestTravelDate: LocalDate,
    // 프론트에서 "여행방이름 (날짜)" 형태로 렌더링. 방이 없는 극단 케이스 대비 nullable.
    val latestGroupName: String?,
)
