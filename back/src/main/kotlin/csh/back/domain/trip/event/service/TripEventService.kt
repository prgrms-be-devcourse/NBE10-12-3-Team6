package csh.back.domain.trip.event.service

import csh.back.domain.trip.event.dto.TripEvent
import csh.back.domain.trip.group.exception.NonMemberException
import csh.back.domain.trip.group.exception.NotFoundException
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Service
class TripEventService(
    private val jdbcTemplate: JdbcTemplate,
    private val emitterFactory: TripSseEmitterFactory,
) {

    private data class Subscriber(
        val memberId: Long,
        val emitter: SseEmitter,
        val scope: SubscriptionScope,
    )

    private enum class SubscriptionScope {
        TRIP,
        TIMELINE,
    }

    private val subscribers = ConcurrentHashMap<Long, CopyOnWriteArrayList<Subscriber>>()
    private val heartbeatScheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, HEARTBEAT_THREAD_NAME).apply {
                isDaemon = true
            }
        }
    private var heartbeatTask: ScheduledFuture<*>? = null

    @PostConstruct
    fun startHeartbeat() {
        heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(
            ::sendHeartbeatEvents,
            HEARTBEAT_INTERVAL_SECONDS,
            HEARTBEAT_INTERVAL_SECONDS,
            TimeUnit.SECONDS,
        )
    }

    @PreDestroy
    fun stopHeartbeat() {
        heartbeatTask?.cancel(true)
        heartbeatScheduler.shutdownNow()
        subscribers.values
            .flatten()
            .forEach { subscriber ->
                runCatching {
                    subscriber.emitter.complete()
                }
            }
        subscribers.clear()
    }

    fun subscribe(tripGroupId: Long, memberId: Long): SseEmitter {
        return subscribe(
            tripGroupId = tripGroupId,
            memberId = memberId,
            scope = SubscriptionScope.TRIP,
        )
    }

    fun subscribeTimeline(tripGroupId: Long, memberId: Long): SseEmitter {
        return subscribe(
            tripGroupId = tripGroupId,
            memberId = memberId,
            scope = SubscriptionScope.TIMELINE,
        )
    }

    private fun subscribe(
        tripGroupId: Long,
        memberId: Long,
        scope: SubscriptionScope,
    ): SseEmitter {
        validateTripMember(tripGroupId, memberId)

        val emitter = emitterFactory.create(DEFAULT_TIMEOUT)
        val subscriber = Subscriber(
            memberId = memberId,
            emitter = emitter,
            scope = scope,
        )
        subscribers.computeIfAbsent(tripGroupId) {
            CopyOnWriteArrayList()
        }.add(subscriber)

        emitter.onCompletion {
            removeSubscriber(tripGroupId, subscriber)
        }
        emitter.onTimeout {
            removeSubscriber(tripGroupId, subscriber)
        }
        emitter.onError {
            removeSubscriber(tripGroupId, subscriber)
        }

        sendEvent(
            tripGroupId = tripGroupId,
            subscriber = subscriber,
            event = SseEmitter.event()
                .name(CONNECTED_EVENT)
                .data("여행방 변경 알림 연결 완료"),
        )

        return emitter
    }

    fun publishAfterCommit(event: TripEvent) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        publish(event)
                    }
                },
            )
            return
        }

        publish(event)
    }

    private fun publish(event: TripEvent) {
        val tripSubscribers = subscribers[event.tripGroupId]
            ?.takeIf { subscriberList -> subscriberList.isNotEmpty() }
            ?: return

        tripSubscribers
            .asSequence()
            .filter { subscriber ->
                shouldDeliverTripEvent(
                    subscriberMemberId = subscriber.memberId,
                    actorMemberId = event.actorMemberId,
                )
            }
            .filter { subscriber ->
                subscriber.scope == SubscriptionScope.TRIP || event.eventType.affectsTimeline()
            }
            .forEach { subscriber ->
                sendEvent(
                    tripGroupId = event.tripGroupId,
                    subscriber = subscriber,
                    event = SseEmitter.event()
                        .id(event.eventId)
                        .name(
                            when (subscriber.scope) {
                                SubscriptionScope.TRIP -> TRIP_EVENT
                                SubscriptionScope.TIMELINE -> TIMELINE_UPDATED_EVENT
                            },
                        )
                        .data(event),
                )
            }
    }

    internal fun sendHeartbeatEvents() {
        subscribers.forEach { (tripGroupId, tripSubscribers) ->
            tripSubscribers.forEach { subscriber ->
                sendEvent(
                    tripGroupId = tripGroupId,
                    subscriber = subscriber,
                    event = SseEmitter.event()
                        .name(HEARTBEAT_EVENT)
                        .data(
                            mapOf(
                                "sentAt" to Instant.now().toString(),
                            ),
                        ),
                )
            }
        }
    }

    private fun sendEvent(
        tripGroupId: Long,
        subscriber: Subscriber,
        event: SseEmitter.SseEventBuilder,
    ) {
        try {
            subscriber.emitter.send(event)
        } catch (exception: IOException) {
            removeSubscriber(tripGroupId, subscriber)
            runCatching {
                subscriber.emitter.completeWithError(exception)
            }
        } catch (_: IllegalStateException) {
            removeSubscriber(tripGroupId, subscriber)
        }
    }

    private fun removeSubscriber(
        tripGroupId: Long,
        subscriber: Subscriber,
    ) {
        val tripSubscribers = subscribers[tripGroupId] ?: return
        tripSubscribers.remove(subscriber)

        if (tripSubscribers.isEmpty()) {
            subscribers.remove(tripGroupId, tripSubscribers)
        }
    }

    private fun validateTripMember(tripGroupId: Long, memberId: Long) {
        val tripGroupCount = jdbcTemplate.queryForObject(
            "select count(*) from trip_groups where id = ?",
            Int::class.java,
            tripGroupId,
        )
        if (tripGroupCount == null || tripGroupCount == 0) {
            throw NotFoundException("존재하지 않는 모임입니다.")
        }

        val tripMemberCount = jdbcTemplate.queryForObject(
            "select count(*) from trip_members where trip_group_id = ? and member_id = ?",
            Int::class.java,
            tripGroupId,
            memberId,
        )

        if (tripMemberCount == null || tripMemberCount == 0) {
            throw NonMemberException("해당 모임의 멤버가 아닙니다.")
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT = 60L * 60L * 1000L
        const val HEARTBEAT_INTERVAL_SECONDS = 30L
        const val HEARTBEAT_THREAD_NAME = "trip-sse-heartbeat"
        const val CONNECTED_EVENT = "CONNECTED"
        const val HEARTBEAT_EVENT = "HEARTBEAT"
        const val TRIP_EVENT = "TRIP_EVENT"
        const val TIMELINE_UPDATED_EVENT = "TIMELINE_UPDATED"
    }
}

internal fun shouldDeliverTripEvent(
    subscriberMemberId: Long,
    actorMemberId: Long?,
): Boolean = actorMemberId == null || subscriberMemberId != actorMemberId
