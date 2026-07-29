package csh.back.domain.trip.place.dto.request

data class TripPlaceSaveRequest(
    val name: String,
    val category: String,
    val address: String,
    val kakaoPlaceId: String,
    val kakaoMapUrl: String,
)