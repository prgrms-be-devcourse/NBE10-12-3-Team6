package csh.back.domain.trip.event.dto

import csh.back.domain.trip.event.enums.TripEventType
import java.time.Instant
import java.util.UUID

data class TripEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val eventType: TripEventType,
    val message: String,
    val tripGroupId: Long,
    val actorMemberId: Long?,
    val dayNumber: Long? = null,
    val timelineId: Long? = null,
    val timelineIds: List<Long> = emptyList(),
    val voteId: Long? = null,
    val tripPlaceId: Long? = null,
    val occurredAt: Instant = Instant.now(),
)
