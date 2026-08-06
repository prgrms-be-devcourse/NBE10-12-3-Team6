package csh.back.global.jwt

import csh.back.domain.member.repository.RefreshTokenRepository
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class JwtRefreshTokenRotationTest {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var refreshTokenRepository: RefreshTokenRepository

    companion object {
        private const val LOGIN_URL = "/api/v1/auth/login"
        // 인증이 필요한 GET 엔드포인트 — rotation 트리거용
        private const val PROTECTED_URL = "/api/v1/trips"
        private const val DEFAULT_DEVICE_ID = "rotation-test-device"
    }

    @AfterEach
    fun cleanup() {
        refreshTokenRepository.deleteAll()
    }

    private fun login(deviceId: String = DEFAULT_DEVICE_ID): String {
        val result = mvc.perform(
            post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "admin@admin.com", "password": "1234"}""")
                .cookie(Cookie(CookieNames.DEVICE_ID, deviceId))
        ).andReturn()
        return result.response.getCookie(CookieNames.REFRESH_TOKEN)!!.value
    }

    // accessToken 없이 refreshToken만 보내 rotation을 유도한다
    private fun triggerRotation(oldRefreshToken: String): MockHttpServletResponse {
        return mvc.perform(
            get(PROTECTED_URL)
                .cookie(Cookie(CookieNames.REFRESH_TOKEN, oldRefreshToken))
        ).andReturn().response
    }

    @Test
    @DisplayName("t1: accessToken 갱신 시 refreshToken 값이 바뀐다")
    fun t1() {
        val oldRefreshToken = login()
        val response = triggerRotation(oldRefreshToken)

        val newRefreshToken = response.getCookie(CookieNames.REFRESH_TOKEN)?.value
        assertThat(newRefreshToken).isNotNull()
        assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken)
    }

    @Test
    @DisplayName("t2: 갱신 후 기존 refreshToken으로는 인증이 안 된다 (구 토큰 무효화)")
    fun t2() {
        val oldRefreshToken = login()
        triggerRotation(oldRefreshToken)

        // 구 토큰은 DB에서 삭제됐으므로 필터가 인증을 설정하지 못함 → 401 (미인증)
        mvc.perform(
            get(PROTECTED_URL)
                .cookie(Cookie(CookieNames.REFRESH_TOKEN, oldRefreshToken))
        ).andExpect(status().isUnauthorized())
    }

    @Test
    @DisplayName("t3: 새 refreshToken은 기존과 동일한 deviceId를 유지한다")
    fun t3() {
        val deviceId = "rotation-device-x"
        val oldRefreshToken = login(deviceId = deviceId)
        val response = triggerRotation(oldRefreshToken)

        val newRefreshTokenValue = response.getCookie(CookieNames.REFRESH_TOKEN)!!.value
        val newRt = refreshTokenRepository.findByTokenWithMember(newRefreshTokenValue).get()
        assertThat(newRt.deviceId).isEqualTo(deviceId)
    }

    @Test
    @DisplayName("t4: 새 refreshToken의 expiresAt이 갱신 시점 기준으로 다시 7일로 설정된다 (연장 방식)")
    fun t4() {
        val oldRefreshToken = login()
        val response = triggerRotation(oldRefreshToken)

        val newRefreshTokenValue = response.getCookie(CookieNames.REFRESH_TOKEN)!!.value
        val newRt = refreshTokenRepository.findByTokenWithMember(newRefreshTokenValue).get()
        val expectedExpiry = LocalDateTime.now().plusDays(7)
        assertThat(newRt.expiresAt).isCloseTo(expectedExpiry, within(5, ChronoUnit.SECONDS))
    }
}