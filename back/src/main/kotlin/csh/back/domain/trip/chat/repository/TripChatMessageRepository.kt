package csh.back.domain.trip.chat.repository

import csh.back.domain.trip.chat.entity.TripChatMessage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

interface TripChatMessageRepository :
    JpaRepository<TripChatMessage, Long>,
    TripChatMessageRepositoryCustom {
    @Transactional
    fun deleteAllByTripGroupId(tripGroupId: Long)
}
