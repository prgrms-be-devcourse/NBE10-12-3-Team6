package csh.back.domain.trip.chat.interceptor

import csh.back.domain.trip.chat.support.toAuthFilterDto
import csh.back.domain.trip.member.validator.TripMemberValidator
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Component
class StompChannelInterceptor(
    private val tripMemberValidator: TripMemberValidator,
) : ChannelInterceptor {

    private class RateWindow(@Volatile var windowStartMillis: Long, val count: AtomicInteger)

    private val rateWindows = ConcurrentHashMap<Long, RateWindow>()

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = StompHeaderAccessor.wrap(message)
        val destination = accessor.destination.orEmpty()

        when (accessor.command) {
            StompCommand.SUBSCRIBE -> {
                if (destination.startsWith(SUBSCRIBE_PREFIX)) {
                    val memberId = accessor.user.toAuthFilterDto().id
                    tripMemberValidator.validMember(extractTripGroupId(destination), memberId)
                }
            }

            StompCommand.SEND -> {
                if (destination.startsWith(SEND_PREFIX)) {
                    val memberId = accessor.user.toAuthFilterDto().id
                    tripMemberValidator.validMember(extractTripGroupId(destination), memberId)
                    checkRateLimit(memberId)
                }
            }

            else -> {}
        }

        return message
    }

    private fun extractTripGroupId(destination: String): Long =
        DESTINATION_REGEX.find(destination)?.groupValues?.get(1)?.toLong()
            ?: throw RuntimeException("잘못된 destination입니다.")

    private fun checkRateLimit(memberId: Long) {
        val now = System.currentTimeMillis()
        val window = rateWindows.compute(memberId) { _, existing ->
            if (existing == null || now - existing.windowStartMillis >= RATE_WINDOW_MILLIS) {
                RateWindow(now, AtomicInteger(1))
            } else {
                existing.count.incrementAndGet()
                existing
            }
        }!!

        if (window.count.get() > RATE_LIMIT_COUNT) {
            throw RuntimeException("메시지 전송이 너무 잦습니다. 잠시 후 다시 시도해주세요.")
        }
    }

    // 오래 유휴 상태인 rate window 엔트리 정리 (세션 종료 이벤트 없이도 메모리 누수 방지)
    @Scheduled(fixedDelay = CLEANUP_INTERVAL_MILLIS)
    fun cleanupStaleWindows() {
        val now = System.currentTimeMillis()
        rateWindows.entries.removeIf { now - it.value.windowStartMillis >= RATE_WINDOW_MILLIS }
    }

    private companion object {
        val DESTINATION_REGEX = Regex("""/trips/(\d+)/chat""")
        const val SUBSCRIBE_PREFIX = "/sub/trips/"
        const val SEND_PREFIX = "/pub/trips/"
        const val RATE_WINDOW_MILLIS = 10_000L
        const val RATE_LIMIT_COUNT = 20
        const val CLEANUP_INTERVAL_MILLIS = 60_000L
    }
}
