package csh.back.domain.trip.group.dto.response

import csh.back.domain.trip.group.entity.TripGroup
import java.time.LocalDate

data class TripGroupResponse(
    val id: Long,
    val name: String,
    val ownerId: Long,
    val region: String,
    val joinCode: String,
    val nights: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    companion object {
        @JvmStatic
        fun from(tripGroup: TripGroup): TripGroupResponse = TripGroupResponse(
            id = requireNotNull(tripGroup.id),
            name = tripGroup.name,
            ownerId = requireNotNull(tripGroup.owner.id),
            region = tripGroup.region,
            joinCode = tripGroup.joinCode,
            nights = tripGroup.nights,
            startDate = tripGroup.startDate,
            endDate = tripGroup.endDate,
        )
    }
}
