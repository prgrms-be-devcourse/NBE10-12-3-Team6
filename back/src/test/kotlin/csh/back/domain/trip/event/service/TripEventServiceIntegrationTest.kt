package csh.back.domain.trip.event.service

import csh.back.domain.trip.event.dto.TripEvent
import csh.back.domain.trip.event.enums.TripEventType
import csh.back.domain.trip.group.exception.NonMemberException
import csh.back.domain.trip.group.exception.NotFoundException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

@ActiveProfiles("test")
@SpringBootTest(
    properties = [
        "cloud.aws.credentials.access-key=test",
        "cloud.aws.credentials.secret-key=test",
        "cloud.aws.region.static=ap-northeast-2",
        "cloud.aws.s3.endpoint=http://localhost",
    ],
)
class TripEventServiceIntegrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var emitterFactory: TripSseEmitterFactory
    private lateinit var tripEventService: TripEventService

    @BeforeEach
    fun setUp() {
        emitterFactory = Mockito.mock(TripSseEmitterFactory::class.java)
        Mockito.`when`(emitterFactory.create(anyLong())).thenAnswer { invocation ->
            RecordingSseEmitter(invocation.getArgument(0))
        }
        tripEventService = TripEventService(jdbcTemplate, emitterFactory)
    }

    @AfterEach
    fun tearDown() {
        tripEventService.stopHeartbeat()
    }

    @Test
    @DisplayName("여행방 멤버는 구독 직후 CONNECTED 이벤트를 받고 비멤버는 구독할 수 없다")
    fun subscribesMemberAndRejectsNonMember() {
        val emitter = tripEventService.subscribe(TRIP_GROUP_ID, MEMBER_ID) as RecordingSseEmitter

        assertEquals(60L * 60L * 1000L, emitter.timeout)
        assertEquals(1, emitter.events.size)
        assertEquals("CONNECTED", emitter.events.single().name)
        assertTrue(emitter.events.single().protocolText.contains("여행방 변경 알림 연결 완료"))

        val exception = assertThrows<NonMemberException> {
            tripEventService.subscribe(TRIP_GROUP_ID, NON_MEMBER_ID)
        }
        assertEquals("해당 모임의 멤버가 아닙니다.", exception.message)
    }

    @Test
    @DisplayName("존재하지 않는 여행방은 SSE를 구독할 수 없다")
    fun rejectsMissingTripGroup() {
        val exception = assertThrows<NotFoundException> {
            tripEventService.subscribe(MISSING_TRIP_GROUP_ID, MEMBER_ID)
        }

        assertEquals("존재하지 않는 모임입니다.", exception.message)
    }

    @Test
    @DisplayName("사용자 이벤트는 행위자를 제외한 같은 여행방 멤버에게만 전달한다")
    fun publishesOnlyToOtherTripMembers() {
        val actorEmitter = tripEventService.subscribe(TRIP_GROUP_ID, MEMBER_ID) as RecordingSseEmitter
        val otherEmitter = tripEventService.subscribe(TRIP_GROUP_ID, OTHER_MEMBER_ID) as RecordingSseEmitter
        actorEmitter.clearEvents()
        otherEmitter.clearEvents()
        val event = tripEvent(
            eventId = "other-member-event",
            actorMemberId = MEMBER_ID,
        )

        tripEventService.publishAfterCommit(event)

        assertTrue(actorEmitter.events.isEmpty())
        assertEquals(1, otherEmitter.events.size)
        assertEquals("TRIP_EVENT", otherEmitter.events.single().name)
        assertEquals("other-member-event", otherEmitter.events.single().id)
        assertSame(event, otherEmitter.events.single().payload)
    }

    @Test
    @DisplayName("시스템 이벤트는 같은 여행방의 모든 구독자에게 전달한다")
    fun publishesSystemEventToEverySubscriber() {
        val firstEmitter = tripEventService.subscribe(TRIP_GROUP_ID, MEMBER_ID) as RecordingSseEmitter
        val secondEmitter = tripEventService.subscribe(TRIP_GROUP_ID, OTHER_MEMBER_ID) as RecordingSseEmitter
        firstEmitter.clearEvents()
        secondEmitter.clearEvents()
        val event = tripEvent(
            eventId = "system-event",
            actorMemberId = null,
        )

        tripEventService.publishAfterCommit(event)

        assertSame(event, firstEmitter.events.single().payload)
        assertSame(event, secondEmitter.events.single().payload)
    }

    @Test
    @DisplayName("타임라인 구독자는 타임라인에 영향을 주는 이벤트만 기존 이벤트 이름으로 받는다")
    fun filtersTimelineSubscriptionEvents() {
        val timelineEmitter =
            tripEventService.subscribeTimeline(TRIP_GROUP_ID, MEMBER_ID) as RecordingSseEmitter
        val tripEmitter =
            tripEventService.subscribe(TRIP_GROUP_ID, OTHER_MEMBER_ID) as RecordingSseEmitter
        timelineEmitter.clearEvents()
        tripEmitter.clearEvents()

        tripEventService.publishAfterCommit(
            tripEvent(
                eventId = "wish-place-event",
                eventType = TripEventType.WISH_PLACE_ADDED,
                actorMemberId = null,
            ),
        )

        assertTrue(timelineEmitter.events.isEmpty())
        assertEquals("TRIP_EVENT", tripEmitter.events.single().name)
        tripEmitter.clearEvents()

        tripEventService.publishAfterCommit(
            tripEvent(
                eventId = "timeline-event",
                eventType = TripEventType.TIMELINE_CREATED,
                actorMemberId = null,
            ),
        )

        assertEquals("TIMELINE_UPDATED", timelineEmitter.events.single().name)
        assertEquals("timeline-event", timelineEmitter.events.single().id)
        assertEquals("TRIP_EVENT", tripEmitter.events.single().name)
    }

    @Test
    @DisplayName("트랜잭션 안에서 등록한 이벤트는 커밋이 끝난 뒤 발행한다")
    fun publishesAfterTransactionCommit() {
        val emitter = tripEventService.subscribe(TRIP_GROUP_ID, OTHER_MEMBER_ID) as RecordingSseEmitter
        emitter.clearEvents()
        val event = tripEvent(
            eventId = "after-commit-event",
            actorMemberId = MEMBER_ID,
        )

        TransactionTemplate(transactionManager).executeWithoutResult {
            tripEventService.publishAfterCommit(event)
            assertTrue(emitter.events.isEmpty())
        }

        assertEquals(1, emitter.events.size)
        assertSame(event, emitter.events.single().payload)
    }

    @Test
    @DisplayName("Heartbeat 실행 시 모든 구독자에게 HEARTBEAT 이벤트를 보낸다")
    fun sendsHeartbeatToEverySubscriber() {
        val firstEmitter = tripEventService.subscribe(TRIP_GROUP_ID, MEMBER_ID) as RecordingSseEmitter
        val secondEmitter = tripEventService.subscribe(TRIP_GROUP_ID, OTHER_MEMBER_ID) as RecordingSseEmitter
        firstEmitter.clearEvents()
        secondEmitter.clearEvents()

        tripEventService.sendHeartbeatEvents()

        listOf(firstEmitter, secondEmitter).forEach { emitter ->
            val heartbeat = emitter.events.single()
            assertEquals("HEARTBEAT", heartbeat.name)
            val payload = heartbeat.payload as Map<*, *>
            assertFalse(payload["sentAt"].toString().isBlank())
        }
    }

    @Test
    @DisplayName("완료, 타임아웃, 오류가 발생한 연결은 구독 목록에서 제거한다")
    fun removesClosedConnections() {
        val completedEmitter =
            tripEventService.subscribe(TRIP_GROUP_ID, MEMBER_ID) as RecordingSseEmitter
        val timedOutEmitter =
            tripEventService.subscribe(TRIP_GROUP_ID, MEMBER_ID) as RecordingSseEmitter
        val failedEmitter =
            tripEventService.subscribe(TRIP_GROUP_ID, MEMBER_ID) as RecordingSseEmitter
        completedEmitter.clearEvents()
        timedOutEmitter.clearEvents()
        failedEmitter.clearEvents()

        completedEmitter.simulateCompletion()
        timedOutEmitter.simulateTimeout()
        failedEmitter.simulateError(IllegalStateException("connection closed"))
        tripEventService.publishAfterCommit(
            tripEvent(
                eventId = "event-after-close",
                actorMemberId = null,
            ),
        )

        assertTrue(completedEmitter.events.isEmpty())
        assertTrue(timedOutEmitter.events.isEmpty())
        assertTrue(failedEmitter.events.isEmpty())
    }

    @Test
    @DisplayName("이벤트 전송 중 IOException이 발생하면 연결을 완료하고 구독 목록에서 제거한다")
    fun removesConnectionWhenSendingFails() {
        val emitter = tripEventService.subscribe(TRIP_GROUP_ID, MEMBER_ID) as RecordingSseEmitter
        emitter.clearEvents()
        emitter.failOnSend = true

        tripEventService.publishAfterCommit(
            tripEvent(
                eventId = "failing-event",
                actorMemberId = null,
            ),
        )

        assertNotNull(emitter.completedWithError)
        emitter.failOnSend = false
        tripEventService.publishAfterCommit(
            tripEvent(
                eventId = "event-after-failure",
                actorMemberId = null,
            ),
        )
        assertTrue(emitter.events.isEmpty())
    }

    private fun tripEvent(
        eventId: String,
        eventType: TripEventType = TripEventType.TRIP_GROUP_UPDATED,
        actorMemberId: Long?,
    ): TripEvent =
        TripEvent(
            eventId = eventId,
            eventType = eventType,
            message = "테스트 이벤트",
            tripGroupId = TRIP_GROUP_ID,
            actorMemberId = actorMemberId,
        )

    private companion object {
        const val TRIP_GROUP_ID = 1L
        const val MEMBER_ID = 1L
        const val OTHER_MEMBER_ID = 3L
        const val NON_MEMBER_ID = 2L
        const val MISSING_TRIP_GROUP_ID = 999L
    }
}

private data class RecordedSseEvent(
    val name: String?,
    val id: String?,
    val payload: Any?,
    val protocolText: String,
)

private class RecordingSseEmitter(
    timeout: Long,
) : SseEmitter(timeout) {

    val events = CopyOnWriteArrayList<RecordedSseEvent>()
    var failOnSend: Boolean = false
    var completedWithError: Throwable? = null

    private val completionCallbacks = CopyOnWriteArrayList<Runnable>()
    private val timeoutCallbacks = CopyOnWriteArrayList<Runnable>()
    private val errorCallbacks = CopyOnWriteArrayList<Consumer<Throwable>>()

    override fun send(builder: SseEventBuilder) {
        if (failOnSend) {
            throw IOException("test connection failure")
        }

        val parts = builder.build().map { part -> part.data }
        val protocolText = parts
            .filterIsInstance<String>()
            .joinToString(separator = "")
        events += RecordedSseEvent(
            name = EVENT_NAME_REGEX.find(protocolText)?.groupValues?.get(1),
            id = EVENT_ID_REGEX.find(protocolText)?.groupValues?.get(1),
            payload = parts.firstOrNull { part ->
                part is TripEvent || part is Map<*, *>
            },
            protocolText = protocolText,
        )
    }

    override fun onCompletion(callback: Runnable) {
        completionCallbacks += callback
    }

    override fun onTimeout(callback: Runnable) {
        timeoutCallbacks += callback
    }

    override fun onError(callback: Consumer<Throwable>) {
        errorCallbacks += callback
    }

    override fun completeWithError(ex: Throwable) {
        completedWithError = ex
    }

    fun clearEvents() {
        events.clear()
    }

    fun simulateCompletion() {
        completionCallbacks.forEach(Runnable::run)
    }

    fun simulateTimeout() {
        timeoutCallbacks.forEach(Runnable::run)
    }

    fun simulateError(error: Throwable) {
        errorCallbacks.forEach { callback -> callback.accept(error) }
    }

    private companion object {
        val EVENT_NAME_REGEX = Regex("""(?:^|\n)event:([^\n]+)""")
        val EVENT_ID_REGEX = Regex("""(?:^|\n)id:([^\n]+)""")
    }
}
