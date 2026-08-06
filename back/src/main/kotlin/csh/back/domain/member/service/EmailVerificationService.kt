package csh.back.domain.member.service

import csh.back.domain.member.exception.EmailVerificationException
import csh.back.domain.member.exception.ExistingMemberException
import csh.back.domain.member.repository.MemberRepository
import csh.back.global.mail.EmailCooldownGuard
import csh.back.global.mail.MailService
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import org.slf4j.LoggerFactory

@Service
class EmailVerificationService(
    private val mailService: MailService,
    private val emailCooldownGuard: EmailCooldownGuard,
    private val redisTemplate: StringRedisTemplate,
    private val memberRepository: MemberRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val CODE_TTL_MINUTES = 5L     // 인증 코드 유효 시간 (분)
        private const val COOLDOWN_SECONDS = 30L    // 재발송 제한 시간 (초)
        private const val CODE_KEY_PREFIX = "email:verification:"  // 인증 코드 Redis 키
        private const val COOLDOWN_PURPOSE = "verification"        // 쿨다운 purpose 키
    }

    fun sendVerificationCode(email: String) {
        // 이미 가입된 이메일이면 발송 거부
        if (memberRepository.existsByEmail(email)) {
            throw ExistingMemberException("이미 사용 중인 이메일입니다.")
        }

        // 연속 호출 방지 — 쿨다운 남아 있으면 예외 발생
        emailCooldownGuard.check(COOLDOWN_PURPOSE, email)

        val code = (100_000..999_999).random().toString()
        log.debug("이메일 인증 코드 발급: {}", code)
        // 인증 코드를 Redis에 저장 (5분 TTL)
        redisTemplate.opsForValue().set(
            "$CODE_KEY_PREFIX$email",
            code,
            Duration.ofMinutes(CODE_TTL_MINUTES),
        )

        // SMTP는 잘못된 주소에도 발송을 시도하므로, 남용/부하 방지를 위해 발송 전 쿨다운 선점
        emailCooldownGuard.mark(COOLDOWN_PURPOSE, email, COOLDOWN_SECONDS)

        mailService.sendHtmlEmail(
            to = email,
            subject = "[Triplog] 이메일 인증 코드",
            contentHtml = buildVerificationContent(code),
        )
    }

    fun verifyCode(email: String, code: String) {
        val key = "$CODE_KEY_PREFIX$email"

        // Redis에 코드가 없으면 만료 또는 미발송 상태
        val stored = redisTemplate.opsForValue().get(key)
            ?: throw EmailVerificationException("인증 코드가 만료되었거나 존재하지 않습니다.")

        if (stored != code) {
            throw EmailVerificationException("인증 코드가 일치하지 않습니다.")
        }

        // 인증 성공 시 코드 즉시 삭제 (재사용 방지)
        redisTemplate.delete(key)
    }

    private fun buildVerificationContent(code: String) = """
        <p style="margin:0 0 8px;color:#333333;font-size:18px;font-weight:600;">이메일 인증 코드</p>
        <p style="margin:0 0 32px;color:#888888;font-size:14px;line-height:1.6;">
          아래 인증 코드를 입력창에 입력해 주세요.<br>코드는 <strong>${CODE_TTL_MINUTES}분</strong> 후 만료됩니다.
        </p>

        <!-- 코드 박스 -->
        <div style="background-color:#f0f5ff;border:2px dashed #4A90E2;border-radius:8px;padding:24px;text-align:center;margin-bottom:32px;">
          <span style="font-size:36px;font-weight:700;color:#4A90E2;letter-spacing:8px;">$code</span>
        </div>

        <p style="margin:0;color:#aaaaaa;font-size:12px;line-height:1.6;">
          본인이 요청하지 않은 경우 이 메일을 무시해 주세요.<br>
          인증 코드를 타인에게 공유하지 마세요.
        </p>
    """.trimIndent()
}
