package csh.back.global.filter

import csh.back.global.jwt.CookieNames
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.UUID

// @Component 미사용 — SecurityConfig에서 Security 필터 체인에 직접 등록해 실행 순서를 명확히 제어
class DeviceIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val deviceId = request.cookies?.find { it.name == CookieNames.DEVICE_ID }?.value
            ?: UUID.randomUUID().toString().also { newId ->
                // 10년 maxAge: 로그아웃해도 쿠키가 만료되지 않아 동일 브라우저를 같은 기기로 인식
                // (Integer.MAX_VALUE 초 ≈ 68년이 상한이지만 10년으로 충분, Jakarta Cookie.setMaxAge가 int)
                response.addHeader(
                    HttpHeaders.SET_COOKIE,
                    ResponseCookie.from(CookieNames.DEVICE_ID, newId)
                        .httpOnly(true)
                        .path("/")
                        .maxAge(Duration.ofDays(3650))
                        .sameSite("Lax")
                        .build()
                        .toString()
                )
            }
        // 같은 요청 안에서 JwtAuthenticationFilter, Controller, OAuth2SuccessHandler가 device_id를 읽을 수 있도록 attribute에 저장
        request.setAttribute(CookieNames.DEVICE_ID, deviceId)
        filterChain.doFilter(request, response)
    }
}