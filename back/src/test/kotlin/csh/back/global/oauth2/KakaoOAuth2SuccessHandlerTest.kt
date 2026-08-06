package csh.back.global.oauth2

import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.member.repository.RefreshTokenRepository
import csh.back.global.jwt.CookieNames
import csh.back.global.mail.EmailCooldownGuard
import csh.back.global.mail.MailService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

@ActiveProfiles("test")
@SpringBootTest
class KakaoOAuth2SuccessHandlerTest {

    @Autowired
    private lateinit var handler: KakaoOAuth2SuccessHandler

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    // notifyIfNewDevice()가 Redis(EmailCooldownGuard)·SMTP(MailService)를 사용하므로 차단
    @MockitoBean
    private lateinit var emailCooldownGuard: EmailCooldownGuard

    @MockitoBean
    private lateinit var mailService: MailService

    @AfterEach
    fun cleanup() {
        refreshTokenRepository.deleteAll()
        memberRepository.deleteAll(
            memberRepository.findAll().filter { it.provider == "KAKAO" }
        )
    }

    // 카카오 서버 호출 없이 OAuth2User + Authentication을 직접 조립
    // user-name-attribute: id 설정과 동일하게 nameAttributeKey = "id"
    private fun createAuthentication(kakaoId: String, nickname: String = "테스트닉네임") =
        UsernamePasswordAuthenticationToken(
            DefaultOAuth2User(
                setOf(SimpleGrantedAuthority("ROLE_USER")),
                mapOf(
                    "id" to kakaoId.toLong(),
                    "kakao_account" to mapOf("profile" to mapOf("nickname" to nickname))
                ),
                "id"
            ),
            null
        )

    private fun mockRequest(deviceId: String = "test-device-uuid") = MockHttpServletRequest().apply {
        addHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 TestBrowser")
        // DeviceIdFilter 없이 핸들러를 직접 호출하므로 attribute를 수동으로 설정
        setAttribute(CookieNames.DEVICE_ID, deviceId)
    }

    @Test
    @DisplayName("t1 - 카카오 로그인 성공 시 Member가 provider=KAKAO, providerId=카카오 회원번호로 생성됨")
    fun t1() {
        val kakaoId = "111111111"

        val response = MockHttpServletResponse()

        handler.onAuthenticationSuccess(mockRequest(), response, createAuthentication(kakaoId, "닉네임A"))

        val member = memberRepository.findByProviderAndProviderId("KAKAO", kakaoId)
        assertThat(member).isPresent
        assertThat(member.get().provider).isEqualTo("KAKAO")
        assertThat(member.get().providerId).isEqualTo(kakaoId)
        assertThat(member.get().name).isEqualTo("닉네임A")
        assertThat(member.get().email).isEqualTo("kakao_${kakaoId}@triplog.local")
        assertThat(response.redirectedUrl).isEqualTo("http://localhost:3000?oauth=success")
    }

    @Test
    @DisplayName("t2 - 이미 가입된 카카오 회원이 재로그인 시 기존 Member 재사용 (중복 생성 안 됨)")
    fun t2() {
        val kakaoId = "222222222"
        val authentication = createAuthentication(kakaoId)

        handler.onAuthenticationSuccess(mockRequest(), MockHttpServletResponse(), authentication)
        handler.onAuthenticationSuccess(mockRequest(), MockHttpServletResponse(), authentication)

        val kakaoMembers = memberRepository.findAll().filter { it.providerId == kakaoId }
        assertThat(kakaoMembers).hasSize(1)
    }

    @Test
    @DisplayName("t3 - 카카오 로그인 성공 시 RefreshToken이 해당 Member와 연결되고 expiresAt이 7일 후")
    fun t3() {
        val kakaoId = "333333333"
        val beforeLogin = LocalDateTime.now()

        handler.onAuthenticationSuccess(mockRequest(), MockHttpServletResponse(), createAuthentication(kakaoId))

        val member = memberRepository.findByProviderAndProviderId("KAKAO", kakaoId).get()
        // findByTokenWithMember: JOIN FETCH로 member까지 로드해 lazy loading 없이 안전하게 접근
        val tokenValue = refreshTokenRepository.findAll().first().token
        val token = refreshTokenRepository.findByTokenWithMember(tokenValue).get()

        assertThat(token.member.id).isEqualTo(member.id)
        assertThat(token.expiresAt).isBetween(
            beforeLogin.plusDays(7).minusSeconds(1),
            beforeLogin.plusDays(7).plusSeconds(1)
        )
    }
}
