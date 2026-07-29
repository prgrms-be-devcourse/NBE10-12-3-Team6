package csh.back.domain.trip.place.repository

import csh.back.domain.trip.place.entity.TripPlace
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface TripPlaceRepository : JpaRepository<TripPlace, Long> {
    fun findAllByTripGroupId(tripGroupId: Long): List<TripPlace>

    // 확정하려는 후보 장소가 해당 여행 모임에 속하는지 확인하면서 조회
    fun findByIdAndTripGroupId(confirmedPlaceId: Long, tripGroupId: Long): Optional<TripPlace>
}