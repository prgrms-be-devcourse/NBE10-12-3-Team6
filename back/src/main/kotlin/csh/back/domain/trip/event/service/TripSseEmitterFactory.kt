package csh.back.domain.trip.event.service

import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Component
class TripSseEmitterFactory {

    fun create(timeout: Long): SseEmitter = SseEmitter(timeout)
}
