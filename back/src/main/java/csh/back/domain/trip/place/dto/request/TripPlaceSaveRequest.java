package csh.back.domain.trip.place.dto.request;

public record TripPlaceSaveRequest(String name,
                                   String category,
                                   String address,
                                   String kakaoPlaceId,
                                   String kakaoMapUrl
    ) {

    }