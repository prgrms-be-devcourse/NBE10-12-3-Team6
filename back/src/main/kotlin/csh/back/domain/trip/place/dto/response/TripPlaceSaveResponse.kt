package csh.back.domain.trip.place.dto.response

import csh.back.domain.trip.place.entity.TripPlace

data class TripPlaceSaveResponse(
    val id: Long,
    val name: String,
    val category: String,
    val address: String,
) {
    companion object {
        @JvmStatic
        fun from(tripPlace: TripPlace): TripPlaceSaveResponse = TripPlaceSaveResponse(
            id = requireNotNull(tripPlace.id),
            name = tripPlace.name,
            category = tripPlace.category,
            address = tripPlace.address,
        )
    }
}