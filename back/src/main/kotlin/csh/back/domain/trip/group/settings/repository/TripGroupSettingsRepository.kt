package csh.back.domain.trip.group.settings.repository

import csh.back.domain.trip.group.settings.entity.TripGroupSettings
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface TripGroupSettingsRepository : JpaRepository<TripGroupSettings, Long> {

    fun findByTripGroupId(tripGroupId: Long): Optional<TripGroupSettings>
}
