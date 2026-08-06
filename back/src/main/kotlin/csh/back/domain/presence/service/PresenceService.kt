package csh.back.domain.presence.service

import csh.back.domain.trip.member.repository.TripMemberRepository
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class PresenceService(
    private val redisTemplate: StringRedisTemplate,
    private val tripMemberRepository: TripMemberRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val KEY_PREFIX = "presence:"
        private val TTL: Duration = Duration.ofSeconds(60)
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
    }

    // 연결이 여러 개(멀티 탭 등)일 수 있으므로 connectionId 단위로 추적. 한 유저의 여러 연결 중
    // 하나라도 살아있으면 online 유지.
    private data class Connection(val userId: Long, val emitter: SseEmitter)

    private val connections = ConcurrentHashMap<String, Connection>()

    fun subscribe(userId: Long): SseEmitter {
        // Long.MAX_VALUE로 사실상 무한 타임아웃 — 컨테이너가 관리
        val emitter = SseEmitter(Long.MAX_VALUE)
        val connectionId = UUID.randomUUID().toString()
        connections[connectionId] = Connection(userId, emitter)

        emitter.onCompletion { removeConnection(connectionId) }
        emitter.onTimeout { removeConnection(connectionId) }
        emitter.onError { removeConnection(connectionId) }

        // 최초 접속 시 즉시 online 마킹 + 클라이언트에 확인용 이벤트 하나 발송
        markOnline(userId)
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"))
        } catch (e: Exception) {
            removeConnection(connectionId)
        }
        return emitter
    }

    // 특정 유저의 모든 SSE 연결을 강제 종료 + Redis presence 삭제 (로그아웃 시 사용).
    fun disconnectAll(userId: Long) {
        val toRemove = connections.entries.filter { it.value.userId == userId }
        toRemove.forEach { (connectionId, conn) ->
            try {
                conn.emitter.complete()
            } catch (e: Exception) {
                log.debug("emitter complete failed userId={}", userId, e)
            }
            connections.remove(connectionId)
        }
        redisTemplate.delete("$KEY_PREFIX$userId")
    }

    // 단건 조회
    fun isOnline(userId: Long): Boolean =
        redisTemplate.hasKey("$KEY_PREFIX$userId") == true

    // 배치 조회. 프라이버시 스코프: requester가 함께 여행한 적 있는 회원(자기 자신 포함)만 반환.
    // 볼 수 없는 userId는 응답에서 제외.
    fun getOnlineStatus(requesterId: Long, userIds: List<Long>): Map<Long, Boolean> {
        if (userIds.isEmpty()) return emptyMap()

        val related = tripMemberRepository
            .findRelatedMemberIds(requesterId, userIds)
            .toSet() + requesterId
        val visibleIds = userIds.filter { it in related }

        if (visibleIds.isEmpty()) return emptyMap()

        val keys = visibleIds.map { "$KEY_PREFIX$it" }
        val values = redisTemplate.opsForValue().multiGet(keys) ?: return visibleIds.associateWith { false }
        return visibleIds.zip(values).associate { (id, v) -> id to (v != null) }
    }

    // 30초마다: 활성 연결에 keepalive ping을 보내고, 살아있는 연결의 userId TTL을 갱신.
    // 죽은 커넥션은 자연스럽게 걷어냄.
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    fun heartbeat() {
        val onlineUserIds = mutableSetOf<Long>()
        val deadConnectionIds = mutableListOf<String>()

        connections.forEach { (connectionId, conn) ->
            try {
                conn.emitter.send(SseEmitter.event().comment("ping"))
                onlineUserIds.add(conn.userId)
            } catch (e: Exception) {
                deadConnectionIds.add(connectionId)
            }
        }

        deadConnectionIds.forEach { removeConnection(it) }
        onlineUserIds.forEach { markOnline(it) }
    }

    private fun markOnline(userId: Long) {
        redisTemplate.opsForValue().set("$KEY_PREFIX$userId", "1", TTL)
    }

    private fun removeConnection(connectionId: String) {
        connections.remove(connectionId)
    }

    // 앱 종료 시 열려있는 모든 연결을 정리 (강제 종료 시엔 무시됨)
    @PreDestroy
    fun shutdown() {
        connections.values.forEach { conn ->
            try {
                conn.emitter.complete()
            } catch (e: Exception) {
                log.debug("emitter complete failed on shutdown", e)
            }
        }
        connections.clear()
    }
}
