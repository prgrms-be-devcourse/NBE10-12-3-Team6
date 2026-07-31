package csh.back.global.oauth2

import csh.back.domain.member.entity.RefreshToken
import csh.back.domain.member.repository.RefreshTokenRepository
import csh.back.domain.member.service.MemberService
import csh.back.global.jwt.CookieNames
import csh.back.global.jwt.JwtUtil
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class KakaoOAuth2SuccessHandler(
    private val memberService: MemberService,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtUtil: JwtUtil,
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val oauth2User = authentication.principal as OAuth2User

        // user-name-attribute: id 설정에 의해 oauth2User.name = 카카오 회원번호(String)
        val kakaoId = oauth2User.name
        @Suppress("UNCHECKED_CAST")
        val kakaoAccount = oauth2User.attributes["kakao_account"] as? Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val profile = kakaoAccount?.get("profile") as? Map<String, Any?>
        val nickname = profile?.get("nickname") as? String ?: "카카오사용자"

        val member = memberService.findOrCreateKakaoMember(kakaoId, nickname)

        val accessToken = jwtUtil.generateAccessToken(member.id!!, member.email)
        // 이 request는 카카오 → 브라우저 → 백엔드 리다이렉트이므로 실제 브라우저 User-Agent가 담겨 있음
        val userAgent = request.getHeader(HttpHeaders.USER_AGENT)
        val refreshToken = refreshTokenRepository.save(RefreshToken(member = member, userAgent = userAgent))

        response.addHeader(HttpHeaders.SET_COOKIE,
            ResponseCookie.from(CookieNames.ACCESS_TOKEN, accessToken)
                .httpOnly(true).path("/").maxAge(Duration.ofMinutes(30)).sameSite("Lax").build().toString())
        response.addHeader(HttpHeaders.SET_COOKIE,
            ResponseCookie.from(CookieNames.REFRESH_TOKEN, refreshToken.token)
                .httpOnly(true).path("/").maxAge(Duration.ofDays(7)).sameSite("Lax").build().toString())

        // TODO: 하드코딩 제거 — 프론트 배포 URL 확정 후 설정값으로 분리
        response.sendRedirect("http://localhost:3000/")
    }
}