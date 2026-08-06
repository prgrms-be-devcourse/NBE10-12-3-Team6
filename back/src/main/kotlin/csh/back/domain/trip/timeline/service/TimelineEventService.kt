package csh.back.domain.trip.timeline.service

import csh.back.domain.trip.event.service.TripEventService
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class TimelineEventService(
    private val tripEventService: TripEventService,
) {

    fun subscribe(tripGroupId: Long, memberId: Long): SseEmitter =
        tripEventService.subscribeTimeline(tripGroupId, memberId)
}
