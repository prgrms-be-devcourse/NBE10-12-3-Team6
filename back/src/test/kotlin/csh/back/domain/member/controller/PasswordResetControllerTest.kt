package csh.back.domain.member.controller

import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.member.repository.PasswordResetTokenRepository
import csh.back.domain.member.repository.RefreshTokenRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// 비로그인 재설정 흐름 MockMvc 통합 테스트.
// - 실 DB(H2)에 recovery code 해시를 세팅한 뒤 verify-code → apply 흐름 전체를 왕복
// - 회원가입 응답에서 raw code를 캡쳐해 비교하는 대신, 테스트에서 직접 code를 부여 → 결정론적
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetControllerTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var passwordResetTokenRepository: PasswordResetTokenRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    // recovery code / 비번 해시 세팅용
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    companion object {
        private const val BASE_URL = "/api/v1/auth/password-reset"
        private const val TEST_EMAIL = "admin@admin.com"
        private const val TEST_CODE = "A3F9K2"     // 테스트 결정론성 위해 고정 코드

        // 응답 body에서 verificationToken 값만 뽑아내는 정규식.
        // 프로젝트가 Jackson 3.x(tools.jackson) 기반이라 com.fasterxml.jackson ObjectMapper가 없어
        // 간단한 정규식으로 추출 (테스트 목적상 충분).
        private val VERIFICATION_TOKEN_REGEX = Regex(""""verificationToken":"([^"]+)"""")
    }

    private fun extractVerificationToken(responseBody: String): String =
        VERIFICATION_TOKEN_REGEX.find(responseBody)?.groupValues?.get(1)
            ?: error("verificationToken not found in response: $responseBody")

    // 각 테스트 실행 전 admin의 recoveryCodeHash를 TEST_CODE의 BCrypt 해시로 세팅.
    // TestInitData가 만든 admin은 recoveryCodeHash가 null이므로 여기서 부여.
    // (@BeforeEach 대신 헬퍼로 뽑아 필요한 테스트만 호출 — 미부여 케이스 테스트도 있음)
    private fun assignTestRecoveryCode() {
        memberRepository.findByEmail(TEST_EMAIL).ifPresent { member ->
            member.assignRecoveryCodeHash(passwordEncoder.encode(TEST_CODE)!!)
            memberRepository.save(member)
        }
    }

    @AfterEach
    fun cleanup() {
        // 이 테스트에서 만든 토큰/세션이 다음 테스트로 leak되지 않도록
        passwordResetTokenRepository.deleteAll()
        refreshTokenRepository.deleteAll()
        // admin 비번을 원상복구 ("1234") — apply 테스트에서 바꾼 상태가 다음 테스트에 영향 안 가도록.
        // updatePassword가 락 카운트/lockedUntil도 자동 리셋해줌.
        // recoveryCodeHash는 각 테스트가 assignTestRecoveryCode()로 필요 시 재부여하므로 여기서 안 건드림.
        memberRepository.findByEmail(TEST_EMAIL).ifPresent { member ->
            member.updatePassword(passwordEncoder.encode("1234")!!)
            memberRepository.save(member)
        }
    }

    // ─── verify-code ────────────────────────────────────────────────────────

    @Test
    @DisplayName("verify-code - 이메일+코드 일치 시 200 + verificationToken 발급")
    fun t1() {
        assignTestRecoveryCode()

        val result = mvc.perform(
            post("$BASE_URL/verify-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$TEST_EMAIL","code":"$TEST_CODE"}""")
        ).andDo(print())
            .andExpect(handler().methodName("verifyCode"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.verificationToken").isString)
            .andReturn()

        // DB에 토큰이 실제로 저장됐는지도 확인
        assertThat(passwordResetTokenRepository.count()).isEqualTo(1L)

        // 응답 토큰이 raw 상태(=URL-safe base64, 20자 이상) 인지 확인
        val token = extractVerificationToken(result.response.contentAsString)
        assertThat(token).matches("^[A-Za-z0-9_-]{20,}$")
    }

    @Test
    @DisplayName("verify-code - 존재하지 않는 이메일이면 400 (통합 메시지)")
    fun t2() {
        mvc.perform(
            post("$BASE_URL/verify-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"notexist@test.com","code":"ABCDEF"}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("이메일 또는 코드가 올바르지 않습니다."))

        assertThat(passwordResetTokenRepository.count()).isZero
    }

    @Test
    @DisplayName("verify-code - 코드 불일치면 400")
    fun t3() {
        assignTestRecoveryCode()

        mvc.perform(
            post("$BASE_URL/verify-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$TEST_EMAIL","code":"WRONG1"}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("이메일 또는 코드가 올바르지 않습니다."))

        assertThat(passwordResetTokenRepository.count()).isZero
    }

    @Test
    @DisplayName("verify-code - 필수 필드 누락(400 validation)")
    fun t4() {
        mvc.perform(
            post("$BASE_URL/verify-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"","code":""}""")
        ).andExpect(status().isBadRequest)
    }

    // ─── apply ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("apply - verify 후 발급받은 토큰으로 새 비밀번호 저장 성공 + 토큰 소진")
    fun t5() {
        assignTestRecoveryCode()

        // verify로 토큰 발급
        val verifyResult = mvc.perform(
            post("$BASE_URL/verify-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$TEST_EMAIL","code":"$TEST_CODE"}""")
        ).andExpect(status().isOk).andReturn()
        val token = extractVerificationToken(verifyResult.response.contentAsString)

        // apply
        mvc.perform(
            post("$BASE_URL/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verificationToken":"$token","newPassword":"newpass1234"}""")
        ).andDo(print())
            .andExpect(handler().methodName("apply"))
            .andExpect(status().isOk)

        // 실제 비번이 바뀌었는지 확인
        val updated = memberRepository.findByEmail(TEST_EMAIL).get()
        assertThat(passwordEncoder.matches("newpass1234", updated.password)).isTrue

        // 토큰은 소진됨(재사용 불가)
        val tokenRow = passwordResetTokenRepository.findAll().first()
        assertThat(tokenRow.isUsed()).isTrue
    }

    @Test
    @DisplayName("apply - 무효 토큰이면 400 (비밀번호 변경 안 됨)")
    fun t6() {
        mvc.perform(
            post("$BASE_URL/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verificationToken":"nonexistent","newPassword":"newpass1234"}""")
        ).andExpect(status().isBadRequest)

        val member = memberRepository.findByEmail(TEST_EMAIL).get()
        // 원래 비번 그대로
        assertThat(passwordEncoder.matches("1234", member.password)).isTrue
    }

    @Test
    @DisplayName("apply - 이미 사용된 토큰으로 재요청 시 400 (1회용 보장)")
    fun t7() {
        assignTestRecoveryCode()

        // 1) verify
        val verifyResult = mvc.perform(
            post("$BASE_URL/verify-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$TEST_EMAIL","code":"$TEST_CODE"}""")
        ).andExpect(status().isOk).andReturn()
        val token = extractVerificationToken(verifyResult.response.contentAsString)

        // 2) 1차 apply (성공)
        mvc.perform(
            post("$BASE_URL/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verificationToken":"$token","newPassword":"newpass1234"}""")
        ).andExpect(status().isOk)

        // 3) 같은 토큰으로 2차 apply — 400
        mvc.perform(
            post("$BASE_URL/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verificationToken":"$token","newPassword":"anotherpass"}""")
        ).andExpect(status().isBadRequest)
    }

    // ─── 통합 시나리오 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("전체 흐름 - 락된 계정도 recovery 흐름 완료 시 락 해제 + 새 비번으로 로그인 가능")
    fun t8() {
        assignTestRecoveryCode()

        // 회원을 락 상태로 만듦 (실패 카운트 5회 세팅 시뮬레이션)
        val member = memberRepository.findByEmail(TEST_EMAIL).get()
        member.registerLoginFailure(5, java.time.Duration.ofMinutes(5))
        member.registerLoginFailure(5, java.time.Duration.ofMinutes(5))
        member.registerLoginFailure(5, java.time.Duration.ofMinutes(5))
        member.registerLoginFailure(5, java.time.Duration.ofMinutes(5))
        member.registerLoginFailure(5, java.time.Duration.ofMinutes(5))
        memberRepository.save(member)
        assertThat(member.isLocked()).isTrue

        // recovery 흐름 완료
        val verifyResult = mvc.perform(
            post("$BASE_URL/verify-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$TEST_EMAIL","code":"$TEST_CODE"}""")
        ).andExpect(status().isOk).andReturn()
        val token = extractVerificationToken(verifyResult.response.contentAsString)

        mvc.perform(
            post("$BASE_URL/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"verificationToken":"$token","newPassword":"unlockedPass"}""")
        ).andExpect(status().isOk)

        // 락 해제 확인 — Member.updatePassword 내부에서 resetLoginFailures() 호출됨
        val after = memberRepository.findByEmail(TEST_EMAIL).get()
        assertThat(after.failedLoginCount).isZero
        assertThat(after.lockedUntil).isNull()
        assertThat(passwordEncoder.matches("unlockedPass", after.password)).isTrue
    }
}
