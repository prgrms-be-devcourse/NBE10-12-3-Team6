package csh.back.domain.member.controller

import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.member.repository.RefreshTokenRepository
import csh.back.domain.member.service.LoginAttemptTracker
import csh.back.global.jwt.CookieNames
import csh.back.global.mail.EmailCooldownGuard
import csh.back.global.mail.MailService
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.then
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.never
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class MemberControllerTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    // 락아웃 시나리오에서 실패 카운트/락 상태를 DB 레벨에서 리셋하기 위해 주입 —
    // MemberService.login()은 REQUIRES_NEW 트랜잭션으로 카운트를 커밋하므로 회원마다 격리 필요
    @Autowired
    private lateinit var memberRepository: MemberRepository

    // login()이 notifyIfNewDevice()를 호출하고, 그 안에서 Redis(EmailCooldownGuard)와 SMTP(MailService)를 사용.
    // MemberControllerTest는 알림 동작이 아닌 로그인/로그아웃 HTTP 동작을 검증하므로 인프라 의존성 차단.
    @MockitoBean
    private lateinit var emailCooldownGuard: EmailCooldownGuard

    @MockitoBean
    private lateinit var mailService: MailService

    companion object {
        private const val BASE_URL = "/api/v1/auth"
    }

    @AfterEach
    fun cleanup() {
        refreshTokenRepository.deleteAll()
        // 락아웃 상태가 다음 테스트에 leak되지 않도록 admin 계정의 카운트/락을 초기화
        memberRepository.findByEmail("admin@admin.com").ifPresent { member ->
            member.resetLoginFailures()
            memberRepository.save(member)
        }
    }

    @Test
    @DisplayName("회원가입 - 정상 + 응답에 6자리 recovery code (첫자리 0 아님, 대문자+숫자) 포함")
    fun t1() {
        mvc.perform(
            post("$BASE_URL/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "newuser@test.com",
                        "password": "password123",
                        "name": "테스트유저"
                    }
                """.trimIndent())
        ).andDo(print())
            .andExpect(handler().handlerType(MemberController::class.java))
            .andExpect(handler().methodName("signUp"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.data.email").value("newuser@test.com"))
            .andExpect(jsonPath("$.data.name").value("테스트유저"))
            // recovery code: 6자리, 첫자리 0 제외 (1-9, A-Z), 나머지 (0-9, A-Z)
            .andExpect(jsonPath("$.data.recoveryCode").value(org.hamcrest.Matchers.matchesPattern("^[1-9A-Z][0-9A-Z]{5}$")))
    }

    @Test
    @DisplayName("회원가입 - 이미 사용 중인 이메일")
    fun t2() {
        mvc.perform(
            post("$BASE_URL/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "admin@admin.com",
                        "password": "1234",
                        "name": "어드민"
                    }
                """.trimIndent())
        ).andDo(print())
            .andExpect(handler().handlerType(MemberController::class.java))
            .andExpect(handler().methodName("signUp"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."))
    }

    @Test
    @DisplayName("회원가입 - 이메일 형식 오류")
    fun t3() {
        mvc.perform(
            post("$BASE_URL/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "invalid-email",
                        "password": "password123",
                        "name": "테스트유저"
                    }
                """.trimIndent())
        ).andDo(print())
            .andExpect(handler().handlerType(MemberController::class.java))
            .andExpect(handler().methodName("signUp"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value(400))
    }

    @Test
    @DisplayName("회원가입 - 필수 필드 누락 (name)")
    fun t4() {
        mvc.perform(
            post("$BASE_URL/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "test@test.com",
                        "password": "password123"
                    }
                """.trimIndent())
        ).andDo(print())
            .andExpect(handler().handlerType(MemberController::class.java))
            .andExpect(handler().methodName("signUp"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("name: must not be blank"))
    }

    @Test
    @DisplayName("로그인 - 정상")
    fun t5() {
        mvc.perform(
            post("$BASE_URL/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "admin@admin.com",
                        "password": "1234"
                    }
                """.trimIndent())
        ).andDo(print())
            .andExpect(handler().handlerType(MemberController::class.java))
            .andExpect(handler().methodName("login"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.data.email").value("admin@admin.com"))
            .andExpect(jsonPath("$.data.name").value("admin"))
            .andExpect(header().exists("Set-Cookie"))
    }

    @Test
    @DisplayName("로그인 - 존재하지 않는 이메일 (계정 열거 방지로 비번 오류와 동일 401 응답)")
    fun t6() {
        mvc.perform(
            post("$BASE_URL/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "notexist@test.com",
                        "password": "1234"
                    }
                """.trimIndent())
        ).andDo(print())
            .andExpect(handler().handlerType(MemberController::class.java))
            .andExpect(handler().methodName("login"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."))
    }

    @Test
    @DisplayName("로그인 - 비밀번호 불일치 (401 + 동일 메시지)")
    fun t7() {
        mvc.perform(
            post("$BASE_URL/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "admin@admin.com",
                        "password": "wrongpassword"
                    }
                """.trimIndent())
        ).andDo(print())
            .andExpect(handler().handlerType(MemberController::class.java))
            .andExpect(handler().methodName("login"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."))
    }

    @Test
    @DisplayName("로그인 - refresh_tokens 테이블에 row 생성")
    fun t8() {
        mvc.perform(
            post("$BASE_URL/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "admin@admin.com", "password": "1234"}""")
        ).andExpect(status().isOk())

        assertThat(refreshTokenRepository.count()).isEqualTo(1L)
    }

    @Test
    @DisplayName("로그인 - 다른 기기(다른 device_id)로 2번 로그인 시 row 2개 생성 (멀티 디바이스)")
    fun t9() {
        // device_id를 명시해 "다른 기기" 시나리오를 의도적으로 구성
        // (device_id 미설정 시 DeviceIdFilter가 매번 새 UUID를 생성해 우연히 통과하는 상황을 방지)
        listOf("device-A", "device-B").forEach { deviceId ->
            mvc.perform(
                post("$BASE_URL/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email": "admin@admin.com", "password": "1234"}""")
                    .cookie(Cookie(CookieNames.DEVICE_ID, deviceId))
            ).andExpect(status().isOk())
        }

        assertThat(refreshTokenRepository.count()).isEqualTo(2L)
    }

    @Test
    @DisplayName("로그아웃 - 해당 기기 토큰만 삭제, 다른 기기 토큰은 유지")
    fun t10() {
        // 첫 번째 로그인 후 쿠키 추출
        val loginResult = mvc.perform(
            post("$BASE_URL/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "admin@admin.com", "password": "1234"}""")
        ).andReturn()
        val accessToken = loginResult.response.getCookie(CookieNames.ACCESS_TOKEN)!!.value
        val refreshToken = loginResult.response.getCookie(CookieNames.REFRESH_TOKEN)!!.value

        // 두 번째 로그인 (다른 기기)
        mvc.perform(
            post("$BASE_URL/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "admin@admin.com", "password": "1234"}""")
        ).andExpect(status().isOk())

        // 첫 번째 기기만 로그아웃
        mvc.perform(
            post("$BASE_URL/logout")
                .cookie(Cookie(CookieNames.ACCESS_TOKEN, accessToken))
                .cookie(Cookie(CookieNames.REFRESH_TOKEN, refreshToken))
        ).andExpect(status().isOk())

        // 두 번째 기기 토큰은 남아있어야 함
        assertThat(refreshTokenRepository.count()).isEqualTo(1L)
    }

    @Test
    @DisplayName("로그아웃 - refreshToken 쿠키 없어도 예외 없이 정상 처리")
    fun t11() {
        // 로그인 후 accessToken만 추출
        val loginResult = mvc.perform(
            post("$BASE_URL/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "admin@admin.com", "password": "1234"}""")
        ).andReturn()
        val accessToken = loginResult.response.getCookie(CookieNames.ACCESS_TOKEN)!!.value

        // refreshToken 쿠키 없이 로그아웃 (쿠키 만료 또는 이중 로그아웃 시나리오)
        mvc.perform(
            post("$BASE_URL/logout")
                .cookie(Cookie(CookieNames.ACCESS_TOKEN, accessToken))
        ).andDo(print())
            .andExpect(status().isOk())
    }

    @Test
    @DisplayName("현재 로그인 회원 조회 - 카카오 로그인과 동일한 쿠키 인증")
    fun t12() {
        val loginResult = mvc.perform(
            post("$BASE_URL/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "admin@admin.com", "password": "1234"}""")
        ).andReturn()

        mvc.perform(
            get("$BASE_URL/me")
                .cookie(
                    loginResult.response.getCookie(CookieNames.ACCESS_TOKEN)!!,
                    loginResult.response.getCookie(CookieNames.REFRESH_TOKEN)!!,
                )
        ).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").isNumber())
            .andExpect(jsonPath("$.data.email").value("admin@admin.com"))
            .andExpect(jsonPath("$.data.name").value("admin"))
    }

    @Test
    @DisplayName("현재 로그인 회원 조회 - 인증 쿠키가 없으면 403")
    fun t13() {
        mvc.perform(get("$BASE_URL/me"))
            .andExpect(status().isForbidden())
    }

    @Test
    @DisplayName("로그인 - Redis 장애 시에도 로그인 성공 (알림만 스킵)")
    fun t14() {
        // Redis 다운을 시뮬레이션: 쿨다운 체크가 RedisConnectionFailureException을 던짐
        willThrow(RedisConnectionFailureException("Unable to connect to Redis"))
            .given(emailCooldownGuard)
            .check(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())

        mvc.perform(
            post("$BASE_URL/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "admin@admin.com", "password": "1234"}""")
        ).andExpect(status().isOk())
            .andExpect(header().exists("Set-Cookie"))

        // 알림은 스킵되어야 하므로 메일 발송 없음
        then(mailService).shouldHaveNoInteractions()
        // 로그인 자체는 정상 완료 — RefreshToken row가 생성됨
        assertThat(refreshTokenRepository.count()).isEqualTo(1L)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 로그인 락아웃 시나리오 (5회 실패 → 5분 락)
    // ─────────────────────────────────────────────────────────────────────────

    // 잘못된 비밀번호로 로그인 시도 — 반환 타입 명시하지 않으면 Kotlin이 Unit으로 추론해 andExpect 체이닝 불가
    private fun attemptWrongPasswordLogin(): ResultActions =
        mvc.perform(
            post("$BASE_URL/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "admin@admin.com", "password": "wrongpassword"}""")
        )

    @Test
    @DisplayName("로그인 - 4회 실패까지는 아직 락 안 걸림 (임계값=5)")
    fun t15() {
        // 4회 반복 실패 — 임계값 미만이라 매번 401
        repeat(LoginAttemptTracker.FAILURE_THRESHOLD - 1) {
            attemptWrongPasswordLogin().andExpect(status().isUnauthorized())
        }

        // 5회째 정상 비밀번호로 로그인하면 성공 + 카운트 리셋
        mvc.perform(
            post("$BASE_URL/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "admin@admin.com", "password": "1234"}""")
        ).andExpect(status().isOk())

        val member = memberRepository.findByEmail("admin@admin.com").get()
        assertThat(member.failedLoginCount).isEqualTo(0)
        assertThat(member.lockedUntil).isNull()
    }

    @Test
    @DisplayName("로그인 - 5회째 실패에서 즉시 429 락아웃 (다음 요청까지 기다리지 않음)")
    fun t16() {
        // 4회는 401 (Invalid Credentials + remainingAttempts)
        repeat(LoginAttemptTracker.FAILURE_THRESHOLD - 1) {
            attemptWrongPasswordLogin().andExpect(status().isUnauthorized())
        }

        // 5회째: 임계값 도달 → 이 요청부터 곧바로 429 반환 (UX 개선)
        attemptWrongPasswordLogin()
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("계정이 잠겼습니다")))
            .andExpect(jsonPath("$.retryAfterSeconds").isNumber())

        // 락 상태에서 정상 비밀번호를 넣어도 여전히 429
        mvc.perform(
            post("$BASE_URL/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "admin@admin.com", "password": "1234"}""")
        ).andExpect(status().isTooManyRequests())

        val member = memberRepository.findByEmail("admin@admin.com").get()
        assertThat(member.failedLoginCount).isGreaterThanOrEqualTo(LoginAttemptTracker.FAILURE_THRESHOLD)
        assertThat(member.lockedUntil).isNotNull()
    }

    @Test
    @DisplayName("로그인 - 락아웃 만료 후 재시도 성공")
    fun t17() {
        // 5회 실패로 락 걸기
        repeat(LoginAttemptTracker.FAILURE_THRESHOLD) {
            attemptWrongPasswordLogin()
        }

        // 실제로 5분 기다리지 않고 도메인 메서드로 락 해제 (시간 조작보다 안전한 방식)
        // — 락 만료의 최종 상태(카운트/lockedUntil 초기화)를 그대로 재현
        val member = memberRepository.findByEmail("admin@admin.com").get()
        member.resetLoginFailures()
        memberRepository.save(member)

        // 정상 로그인 성공
        mvc.perform(
            post("$BASE_URL/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "admin@admin.com", "password": "1234"}""")
        ).andExpect(status().isOk())
    }
}
