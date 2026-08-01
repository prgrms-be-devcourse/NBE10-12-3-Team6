package csh.back.global.jwt

import csh.back.domain.member.dto.response.AuthFilterDto
import csh.back.domain.member.repository.RefreshTokenRepository
import csh.back.domain.member.service.MemberService
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
import java.time.LocalDateTime

class JwtAuthenticationFilter(
    private val jwtUtil: JwtUtil,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val memberService: MemberService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // 1순위: 쿠키에서 토큰 조회
        var accessToken = getCookieValue(request, CookieNames.ACCESS_TOKEN)
        var refreshToken = getCookieValue(request, CookieNames.REFRESH_TOKEN)

        // 2순위: 쿠키에 없으면 Authorization 헤더에서 조회 (v1 프론트 호환용)
        if (accessToken == null && refreshToken == null) {
            val tokensFromHeader = getTokensFromHeader(request)
            refreshToken = tokensFromHeader[0]
            accessToken = tokensFromHeader[1]
        }

        if (accessToken != null && jwtUtil.isValid(accessToken)) {
            setAuthentication(jwtUtil.getEmail(accessToken), jwtUtil.getMemberId(accessToken))
        } else if (refreshToken != null) {
            // JOIN FETCH로 member까지 한 번에 로드 (lazy loading 없이 안전)
            refreshTokenRepository.findByTokenWithMember(refreshToken).ifPresent { rt ->
                if (LocalDateTime.now().isAfter(rt.expiresAt)) {
                    // 만료된 토큰은 인증 없이 통과 (cleanup은 로그아웃 또는 스케줄러에서 처리)
                    return@ifPresent
                }
                val member = rt.member
                val newAccessToken = jwtUtil.generateAccessToken(member.id!!, member.email)
                setAccessTokenCookie(response, newAccessToken)

                // 로그아웃 요청은 rotation 제외: 필터가 구 토큰을 먼저 삭제하면 logout 핸들러가
                // 삭제할 토큰을 찾지 못해 신규 토큰이 DB에 남는 문제 발생
                if (!isLogoutRequest(request)) {
                    val newRt = memberService.rotateRefreshToken(rt)
                    setRefreshTokenCookie(response, newRt.token)
                    response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer ${newRt.token} $newAccessToken")
                } else {
                    response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer ${rt.token} $newAccessToken")
                }

                setAuthentication(member.email, member.id!!)
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun getCookieValue(request: HttpServletRequest, name: String): String? =
        request.cookies?.find { it.name == name }?.value

    // Authorization 헤더에서 "Bearer <refreshToken> <accessToken>" 파싱 (v1 방식 호환)
    // 반환: [0] = refreshToken, [1] = accessToken
    private fun getTokensFromHeader(request: HttpServletRequest): Array<String?> {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return arrayOfNulls(2)
        if (!header.startsWith("Bearer ")) return arrayOfNulls(2)
        val parts = header.substring(7).split(" ")
        return arrayOf(parts.getOrNull(0), parts.getOrNull(1))
    }

    private fun isLogoutRequest(request: HttpServletRequest): Boolean =
        request.method == "POST" && request.requestURI == "/api/v1/auth/logout"

    private fun setAccessTokenCookie(response: HttpServletResponse, accessToken: String) {
        val cookie = ResponseCookie.from(CookieNames.ACCESS_TOKEN, accessToken)
            .httpOnly(true)
            .path("/")
            .maxAge(Duration.ofMinutes(30))
            .sameSite("Lax")
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    private fun setRefreshTokenCookie(response: HttpServletResponse, refreshToken: String) {
        val cookie = ResponseCookie.from(CookieNames.REFRESH_TOKEN, refreshToken)
            .httpOnly(true)
            .path("/")
            .maxAge(Duration.ofDays(7))
            .sameSite("Lax")
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    private fun setAuthentication(email: String, memberId: Long) {
        val principal = AuthFilterDto(memberId, email)
        val authentication = UsernamePasswordAuthenticationToken(
            principal,
            null,
            listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        // details에 memberId를 저장해 컨트롤러에서 DB 재조회 없이 꺼내쓸 수 있게 함
        authentication.details = memberId
        SecurityContextHolder.getContext().authentication = authentication
    }
}