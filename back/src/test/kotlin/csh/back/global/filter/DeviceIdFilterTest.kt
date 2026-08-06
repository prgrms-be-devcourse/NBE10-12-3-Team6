package csh.back.global.filter

import csh.back.domain.member.entity.Member
import csh.back.domain.member.entity.RefreshToken
import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.member.repository.RefreshTokenRepository
import csh.back.global.jwt.CookieNames
import jakarta.servlet.FilterChain
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest
class DeviceIdFilterTest {

    private val filter = DeviceIdFilter()
    // 필터 실행 후 체인 동작은 무관 — noop으로 대체
    private val noopChain = FilterChain { _, _ -> }

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @AfterEach
    fun cleanup() {
        refreshTokenRepository.deleteAll()
        memberRepository.deleteAll(
            memberRepository.findAll().filter { it.email.endsWith("@devicetest.local") }
        )
    }

    @Test
    @DisplayName("t1 - device_id 쿠키 없으면 UUID를 새로 발급해 응답 쿠키와 요청 속성에 저장")
    fun t1() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, noopChain)

        val deviceIdCookie = response.getHeaderValues(HttpHeaders.SET_COOKIE)
            .map { it.toString() }
            .firstOrNull { it.startsWith("${CookieNames.DEVICE_ID}=") }

        assertThat(deviceIdCookie).isNotNull
        val issuedId = request.getAttribute(CookieNames.DEVICE_ID) as String
        assertThat(issuedId).isNotBlank
        assertThat(deviceIdCookie).contains(issuedId)
    }

    @Test
    @DisplayName("t2 - device_id 쿠키가 이미 있으면 기존 값 재사용, 새 쿠키 발급 안 됨")
    fun t2() {
        val existingId = "existing-device-uuid"
        val request = MockHttpServletRequest().apply {
            setCookies(Cookie(CookieNames.DEVICE_ID, existingId))
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, noopChain)

        val newDeviceIdCookieSet = response.getHeaderValues(HttpHeaders.SET_COOKIE)
            .map { it.toString() }
            .any { it.startsWith("${CookieNames.DEVICE_ID}=") }

        assertThat(newDeviceIdCookieSet).isFalse
        assertThat(request.getAttribute(CookieNames.DEVICE_ID)).isEqualTo(existingId)
    }

    @Test
    @DisplayName("t3 - 같은 (member_id, device_id) 조합 중복 저장 시 unique 제약 위반")
    fun t3() {
        val member = memberRepository.save(Member("t3@devicetest.local", "pw", "name"))
        val deviceId = "duplicate-device-id"

        refreshTokenRepository.saveAndFlush(RefreshToken(member = member, deviceId = deviceId))

        assertThrows<DataIntegrityViolationException> {
            refreshTokenRepository.saveAndFlush(RefreshToken(member = member, deviceId = deviceId))
        }
    }

    @Test
    @DisplayName("t4 - 같은 device_id라도 member_id가 다르면 정상 저장 (공용 기기 시나리오)")
    fun t4() {
        val memberA = memberRepository.save(Member("t4a@devicetest.local", "pw", "A"))
        val memberB = memberRepository.save(Member("t4b@devicetest.local", "pw", "B"))
        val deviceId = "shared-device-id"

        refreshTokenRepository.save(RefreshToken(member = memberA, deviceId = deviceId))
        refreshTokenRepository.save(RefreshToken(member = memberB, deviceId = deviceId))

        val tokensOnDevice = refreshTokenRepository.findAll().filter { it.deviceId == deviceId }
        assertThat(tokensOnDevice).hasSize(2)
    }
}