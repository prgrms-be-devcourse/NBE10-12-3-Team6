package csh.back.global.jwt

import csh.back.domain.member.dto.response.AuthFilterDto
import csh.back.domain.member.repository.MemberRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration

class JwtAuthenticationFilter(
    private val jwtUtil: JwtUtil,
    private val memberRepository: MemberRepository
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // 1순위: 쿠키에서 토큰 조회
        var accessToken = getCookieValue(request, CookieNames.ACCESS_TOKEN)
        var refreshToken = getCookieValue(request, CookieNames.REFRESH_TOKEN)

        // 2순위: 쿠키에 없으면 Authorization 헤더에서 조회 (기존 v1 프론트 호환용)
        if (accessToken == null && refreshToken == null) {
            val (tokenFromHeader, accessTokenFromHeader) = getTokensFromHeader(request)
            refreshToken = tokenFromHeader
            accessToken = accessTokenFromHeader
        }

        if (accessToken != null && jwtUtil.isValid(accessToken)) {
            // accessToken 유효 → 인증 처리
            setAuthentication(jwtUtil.getEmail(accessToken), jwtUtil.getMemberId(accessToken))
        } else if (refreshToken != null) {
            // accessToken 만료 또는 없음 → refreshToken으로 DB 조회 후 새 accessToken 발급
            memberRepository.findByRefreshToken(refreshToken).ifPresent { member ->
                val newAccessToken = jwtUtil.generateAccessToken(member.id!!, member.email)
                setAccessTokenCookie(response, newAccessToken)
                response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer ${member.refreshToken} $newAccessToken")
                setAuthentication(member.email, member.id!!)
            }
        }

        filterChain.doFilter(request, response)
    }

    // 요청 쿠키에서 원하는 이름의 값 꺼내기
    private fun getCookieValue(request: HttpServletRequest, name: String): String? {
        return request.cookies?.firstOrNull { it.name == name }?.value
    }

    // Authorization 헤더에서 "Bearer <refreshToken> <accessToken>" 파싱 (v1 방식 호환)
    // 반환: [0] = refreshToken, [1] = accessToken
    private fun getTokensFromHeader(request: HttpServletRequest): Pair<String?, String?> {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header == null || !header.startsWith("Bearer ")) {
            return Pair(null, null)
        }
        val parts = header.substring(7).split(" ")
        val refreshToken = parts.getOrNull(0)
        val accessToken = parts.getOrNull(1)
        return Pair(refreshToken, accessToken)
    }

    // 갱신된 accessToken을 Set-Cookie로 내려주기
    private fun setAccessTokenCookie(response: HttpServletResponse, accessToken: String) {
        val cookie = ResponseCookie.from(CookieNames.ACCESS_TOKEN, accessToken)
            .httpOnly(true)
            .path("/")
            .maxAge(Duration.ofMinutes(30))
            .sameSite("Lax")
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    // SecurityContextHolder에 인증 정보 등록 - principal: AuthFilterDto, details: memberId
    private fun setAuthentication(email: String, memberId: Long) {
        val principal = AuthFilterDto(memberId, email)
        val authentication = UsernamePasswordAuthenticationToken(
            principal,
            null,
            listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        authentication.details = memberId
        SecurityContextHolder.getContext().authentication = authentication
    }
}