package csh.back.domain.trip.place.dto.response

import csh.back.domain.trip.place.entity.TripPlace

data class TripPlaceFindResponse(
    val tripPlaceId: Long,
    val name: String,
    val address: String,
    val category: String,
    val createdBy: String,
) {
    companion object {
        @JvmStatic
        fun from(tripPlace: TripPlace): TripPlaceFindResponse = TripPlaceFindResponse(
            tripPlaceId = requireNotNull(tripPlace.id),
            name = tripPlace.name,
            address = tripPlace.address,
            category = tripPlace.category,
            createdBy = tripPlace.createdBy.member.name,
        )
    }
}