package csh.back.domain.trip.place.dto.response;

import csh.back.domain.trip.place.entity.TripPlace;

public record TripPlaceFindResponse(Long placeId,
                                    String name,
                                    String address,
                                    String category,
                                    String createdBy

                                ) {
        public static TripPlaceFindResponse from(TripPlace tripPlace) {
            return new TripPlaceFindResponse(
                    tripPlace.getId(),
                    tripPlace.getName(),
                    tripPlace.getAddress(),
                    tripPlace.getTheme(),
                    tripPlace.getCreatedBy().getMember().getName()
            );
        }
    }


