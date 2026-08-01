package csh.back.domain.member.service

import csh.back.domain.member.repository.RefreshTokenRepository
import csh.back.global.mail.EmailCooldownGuard
import csh.back.global.mail.MailService
import csh.back.global.mail.exception.MailCooldownException
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class NewDeviceLoginNotificationService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val mailService: MailService,
    private val emailCooldownGuard: EmailCooldownGuard,
) {
    companion object {
        private const val COOLDOWN_PURPOSE = "new_device_login"
        // 로그아웃 후 재로그인 시 동일 기기 중복 알림 방지 — 30일 내 재발송 차단
        private const val COOLDOWN_DAYS_IN_SECONDS = 30L * 24 * 60 * 60
    }

    fun notifyIfNewDevice(memberId: Long, memberEmail: String, deviceId: String, userAgent: String?) {
        // 이미 이 기기의 RefreshToken이 있으면 로그아웃 없이 재로그인한 것 — 알림 불필요
        if (refreshTokenRepository.existsByMemberIdAndDeviceId(memberId, deviceId)) return

        // 쿨다운 중이면 최근 알림을 보낸 기기 (로그아웃 후 재로그인) — 알림 불필요
        val cooldownKey = "$memberId:$deviceId"
        try {
            emailCooldownGuard.check(COOLDOWN_PURPOSE, cooldownKey)
        } catch (e: MailCooldownException) {
            return
        }

        emailCooldownGuard.mark(COOLDOWN_PURPOSE, cooldownKey, COOLDOWN_DAYS_IN_SECONDS)

        mailService.sendHtmlEmail(
            to = memberEmail,
            subject = "[TripLog] 새로운 기기에서 로그인되었습니다",
            contentHtml = buildContent(userAgent),
        )
    }

    private fun buildContent(userAgent: String?): String {
        val loginTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH:mm"))
        val deviceDescription = parseUserAgent(userAgent)

        return """
            <p style="margin:0 0 8px;color:#333333;font-size:18px;font-weight:600;">새로운 기기 로그인 알림</p>
            <p style="margin:0 0 32px;color:#888888;font-size:14px;line-height:1.6;">
              회원님의 계정에 새로운 기기에서 로그인이 감지되었습니다.<br>
              본인이 맞다면 이 메일을 무시해 주세요.
            </p>

            <table width="100%" cellpadding="0" cellspacing="0" style="background-color:#f8f9fa;border-radius:8px;padding:20px;margin-bottom:32px;">
              <tr>
                <td style="padding:8px 0;color:#888888;font-size:13px;width:90px;">로그인 시각</td>
                <td style="padding:8px 0;color:#333333;font-size:13px;font-weight:500;">$loginTime</td>
              </tr>
              <tr>
                <td style="padding:8px 0;color:#888888;font-size:13px;">기기 정보</td>
                <td style="padding:8px 0;color:#333333;font-size:13px;font-weight:500;">$deviceDescription</td>
              </tr>
            </table>

            <p style="margin:0;padding:16px;background-color:#fff3cd;border-radius:8px;color:#856404;font-size:13px;line-height:1.6;">
              ⚠️ 본인이 아니라면 즉시 <strong>비밀번호를 변경</strong>하고 모든 기기에서 로그아웃해 주세요.
            </p>
        """.trimIndent()
    }

    // Edge는 UA에 "Chrome"을 포함하므로 반드시 먼저 확인
    private fun parseUserAgent(userAgent: String?): String {
        if (userAgent == null) return "알 수 없는 기기"

        val os = when {
            userAgent.contains("Windows") -> "Windows"
            userAgent.contains("Macintosh") || userAgent.contains("Mac OS X") -> "Mac"
            userAgent.contains("Android") -> "Android"
            userAgent.contains("iPhone") || userAgent.contains("iPad") -> "iOS"
            userAgent.contains("Linux") -> "Linux"
            else -> "알 수 없는 OS"
        }

        val browser = when {
            userAgent.contains("Edg/") -> "Edge"
            userAgent.contains("Chrome") -> "Chrome"
            userAgent.contains("Firefox") -> "Firefox"
            userAgent.contains("Safari") && !userAgent.contains("Chrome") -> "Safari"
            else -> "알 수 없는 브라우저"
        }

        return "$os · $browser"
    }
}