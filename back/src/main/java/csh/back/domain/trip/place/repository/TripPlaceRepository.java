package csh.back.domain.trip.place.repository;

import csh.back.domain.trip.place.entity.TripPlace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripPlaceRepository extends JpaRepository<TripPlace, Long> {
    List<TripPlace> findAllByTripGroupId(Long tripId);

    // 확정하려는 후보 장소가 해당 여행 모임에 속하는지 확인하면서 조회
    Optional<TripPlace> findByIdAndTripGroupId(Long confirmedPlaceId, Long tripId);
}
