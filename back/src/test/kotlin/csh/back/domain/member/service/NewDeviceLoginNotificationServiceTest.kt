package csh.back.domain.member.service

import csh.back.domain.member.repository.RefreshTokenRepository
import csh.back.global.mail.EmailCooldownGuard
import csh.back.global.mail.MailService
import csh.back.global.mail.exception.MailCooldownException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.never
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

@ActiveProfiles("test")
@SpringBootTest
class NewDeviceLoginNotificationServiceTest {

    @Autowired
    lateinit var notificationService: NewDeviceLoginNotificationService

    @MockitoBean
    lateinit var mailService: MailService

    @MockitoBean
    lateinit var emailCooldownGuard: EmailCooldownGuard

    @MockitoBean
    lateinit var refreshTokenRepository: RefreshTokenRepository

    companion object {
        private const val MEMBER_ID = 1L
        private const val MEMBER_EMAIL = "test@test.com"
        private const val DEVICE_ID = "test-device-001"
        private const val COOLDOWN_KEY = "$MEMBER_ID:$DEVICE_ID"
        private const val COOLDOWN_PURPOSE = "new_device_login"
        private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0"
    }

    // ArgumentMatchers.eq/any는 Mockito 내부적으로 null을 반환해 Kotlin non-null 체크를 위반할 수 있음.
    // matcher를 스택에 등록한 뒤 non-null 값을 반환해 Kotlin 타입 시스템을 만족시킴.
    private fun <T : Any> eqNN(value: T): T {
        ArgumentMatchers.eq(value)
        return value
    }

    private fun anyStringNN(): String {
        ArgumentMatchers.any(String::class.java)
        return ""
    }

    @Test
    @DisplayName("t1: 새 기기, 쿨다운 없음 → 이메일 발송 및 쿨다운 mark")
    fun t1() {
        given(refreshTokenRepository.existsByMemberIdAndDeviceId(MEMBER_ID, DEVICE_ID)).willReturn(false)
        // emailCooldownGuard.check — 예외 없이 통과 (기본 동작)

        notificationService.notifyIfNewDevice(MEMBER_ID, MEMBER_EMAIL, DEVICE_ID, USER_AGENT)

        then(mailService).should().sendHtmlEmail(
            eqNN(MEMBER_EMAIL),
            eqNN("[TripLog] 새로운 기기에서 로그인되었습니다"),
            anyStringNN(),
        )
        then(emailCooldownGuard).should().mark(eqNN(COOLDOWN_PURPOSE), eqNN(COOLDOWN_KEY), eqNN(30L * 24 * 60 * 60))
    }

    @Test
    @DisplayName("t2: 이미 RefreshToken이 있는 기기 (재로그인, 로그아웃 없음) → 이메일 발송 안 됨")
    fun t2() {
        given(refreshTokenRepository.existsByMemberIdAndDeviceId(MEMBER_ID, DEVICE_ID)).willReturn(true)

        notificationService.notifyIfNewDevice(MEMBER_ID, MEMBER_EMAIL, DEVICE_ID, USER_AGENT)

        then(mailService).shouldHaveNoInteractions()
        then(emailCooldownGuard).shouldHaveNoInteractions()
    }

    @Test
    @DisplayName("t3: 쿨다운 중인 기기 (로그아웃 후 재로그인) → 이메일 발송 안 됨")
    fun t3() {
        given(refreshTokenRepository.existsByMemberIdAndDeviceId(MEMBER_ID, DEVICE_ID)).willReturn(false)
        willThrow(MailCooldownException("쿨다운 중")).given(emailCooldownGuard).check(COOLDOWN_PURPOSE, COOLDOWN_KEY)

        notificationService.notifyIfNewDevice(MEMBER_ID, MEMBER_EMAIL, DEVICE_ID, USER_AGENT)

        then(mailService).shouldHaveNoInteractions()
        then(emailCooldownGuard).should(never()).mark(anyStringNN(), anyStringNN(), ArgumentMatchers.anyLong())
    }
}