package csh.back.domain.trip.place.dto.response;

import csh.back.domain.trip.place.entity.TripPlace;

public record TripPlaceSaveResponse(Long id,
                                    String name,
                                    String category,
                                    String address
    ) {
        public static TripPlaceSaveResponse from(TripPlace tripPlace) {
            return new TripPlaceSaveResponse(
                    tripPlace.getId(),
                    tripPlace.getName(),
                    tripPlace.getTheme(),
                    tripPlace.getAddress()
            );
        }
    }