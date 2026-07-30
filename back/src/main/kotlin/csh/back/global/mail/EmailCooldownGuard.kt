package csh.back.global.mail

import csh.back.global.mail.exception.MailCooldownException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class EmailCooldownGuard(
    private val redisTemplate: StringRedisTemplate,
) {
    companion object {
        private const val KEY_PREFIX = "email:cooldown:"
    }

    // 쿨다운이 남아 있으면 남은 시간 안내 후 예외 발생 (발송 전 호출)
    fun check(purpose: String, key: String) {
        val redisKey = buildKey(purpose, key)
        if (redisTemplate.hasKey(redisKey) == true) {
            val remaining = redisTemplate.getExpire(redisKey)
            throw MailCooldownException("${remaining}초 후 재시도해 주세요.")
        }
    }

    // 발송 성공 후 호출 — 만료되면 자동 삭제되어 재발송 가능
    fun mark(purpose: String, key: String, seconds: Long) {
        redisTemplate.opsForValue().set(
            buildKey(purpose, key),
            "1",
            Duration.ofSeconds(seconds),
        )
    }

    private fun buildKey(purpose: String, key: String) = "$KEY_PREFIX$purpose:$key"
}
