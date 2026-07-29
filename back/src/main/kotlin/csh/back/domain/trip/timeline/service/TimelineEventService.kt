package csh.back.domain.trip.timeline.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Service
class TimelineEventService(
    private val jdbcTemplate: JdbcTemplate,
) {

    private val emitters = ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>()

    fun subscribe(tripId: Long, memberId: Long): SseEmitter {
        validateTripMember(tripId, memberId)

        val emitter = SseEmitter(DEFAULT_TIMEOUT)
        emitters.computeIfAbsent(tripId) {
            CopyOnWriteArrayList()
        }.add(emitter)

        emitter.onCompletion {
            removeEmitter(tripId, emitter)
        }
        emitter.onTimeout {
            removeEmitter(tripId, emitter)
        }
        emitter.onError {
            removeEmitter(tripId, emitter)
        }

        try {
            emitter.send(
                SseEmitter.event()
                    .name("CONNECTED")
                    .data("타임라인 변경 알림 연결이 완료되었습니다."),
            )
        } catch (exception: IOException) {
            removeEmitter(tripId, emitter)
            emitter.completeWithError(exception)
        }

        return emitter
    }

    fun sendTimelineUpdatedEventAfterCommit(
        tripId: Long,
        changedMemberId: Long,
    ) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        sendTimelineUpdatedEvent(tripId, changedMemberId)
                    }
                },
            )
            return
        }

        sendTimelineUpdatedEvent(tripId, changedMemberId)
    }

    private fun sendTimelineUpdatedEvent(
        tripId: Long,
        changedMemberId: Long,
    ) {
        val tripEmitters = emitters[tripId]
            ?.takeIf { emitterList -> emitterList.isNotEmpty() }
            ?: return

        tripEmitters.forEach { emitter ->
            try {
                emitter.send(
                    SseEmitter.event()
                        .name("TIMELINE_UPDATED")
                        .data(
                            mapOf(
                                "message" to "새로운 변경 사항이 있습니다.",
                                "changedMemberId" to changedMemberId,
                            ),
                        ),
                )
            } catch (exception: IOException) {
                removeEmitter(tripId, emitter)
                emitter.completeWithError(exception)
            }
        }
    }

    private fun removeEmitter(tripId: Long, emitter: SseEmitter) {
        val tripEmitters = emitters[tripId] ?: return
        tripEmitters.remove(emitter)

        if (tripEmitters.isEmpty()) {
            emitters.remove(tripId, tripEmitters)
        }
    }

    private fun validateTripMember(tripId: Long, memberId: Long) {
        val count = jdbcTemplate.queryForObject(
            "select count(*) from trip_members where trip_group_id = ? and member_id = ?",
            Int::class.java,
            tripId,
            memberId,
        )

        require(count != null && count > 0) {
            "여행 모임 멤버만 접근할 수 있습니다."
        }
    }

    companion object {
        private const val DEFAULT_TIMEOUT = 60L * 60L * 1000L
    }
}
