package csh.back.domain.trip.chat.repository

import csh.back.domain.trip.chat.entity.TripChatReadStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface TripChatReadStatusRepository : JpaRepository<TripChatReadStatus, Long> {
    fun findByTripGroupIdAndMemberId(tripGroupId: Long, memberId: Long): Optional<TripChatReadStatus>
    fun findAllByTripGroupId(tripGroupId: Long): List<TripChatReadStatus>
}
