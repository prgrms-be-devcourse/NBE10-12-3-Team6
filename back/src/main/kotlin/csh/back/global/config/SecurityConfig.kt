package csh.back.global.config

import csh.back.domain.member.repository.RefreshTokenRepository
import csh.back.domain.member.service.MemberService
import csh.back.global.filter.DeviceIdFilter
import csh.back.global.jwt.JwtAuthenticationFilter
import csh.back.global.jwt.JwtUtil
import csh.back.global.oauth2.KakaoOAuth2SuccessHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtUtil: JwtUtil,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val memberService: MemberService,
    private val kakaoOAuth2SuccessHandler: KakaoOAuth2SuccessHandler,
) {

    @Value("\${cors.allowed-origins}")
    private lateinit var allowedOrigins: List<String>

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { cors -> cors.configurationSource(corsConfigurationSource()) }
            .csrf { csrf -> csrf.disable() }
            // OAuth2 인가 요청 중 state 파라미터를 세션에 저장해야 하므로 IF_REQUIRED 사용
            // JWT 필터는 매 요청마다 쿠키에서 토큰을 읽으므로 세션 생성 여부와 무관하게 동작
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            }
            .headers { headers ->
                headers.frameOptions { frame -> frame.sameOrigin() }
            }
            .authorizeHttpRequests { auth ->
                // 회원가입, 로그인은 인증 없이 접근 허용
                auth.requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/swagger-resources/**",
                    "/h2-console/**",
                    "/api/v1/auth/signup",
                    "/api/v1/auth/login",
                    "/api/v1/auth/check_email",
                    "/api/v1/auth/verify_email",
                    // 비밀번호 재설정 요청/검증/확정 — 인증 없이 접근 가능해야 함 (잊어버렸으니 로그인 못 함)
                    "/api/v1/auth/password-reset/**",
                    "/uploadedimages/**",
                    "/actuator/prometheus",
                    "/oauth2/authorization/**",
                ).permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // 그 외 모든 요청은 JWT 필터를 거치되 인증 강제하지 않음
                    .anyRequest().authenticated()
            }
            .oauth2Login { oauth2 ->
                oauth2.successHandler(kakaoOAuth2SuccessHandler)
            }
            .exceptionHandling { ex ->
                // oauth2Login() 기본 EntryPoint는 미인증 요청에 302(로그인 리다이렉트)를 반환
                // API 요청에는 부적절하므로 403으로 직접 응답
                // (GET /oauth2/authorization/kakao는 permitAll이라 이 EntryPoint를 거치지 않음)
                ex.authenticationEntryPoint { _, response, _ ->
                    response.sendError(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN)
                }
            }
            // JwtAuthenticationFilter: Spring 기본 로그인 필터 앞에 위치
            // DeviceIdFilter: JwtAuthenticationFilter보다 먼저 실행되어 device_id를 request attribute에 주입
            .addFilterBefore(JwtAuthenticationFilter(jwtUtil, refreshTokenRepository, memberService), UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(DeviceIdFilter(), JwtAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        configuration.allowedOrigins = allowedOrigins
        configuration.allowedHeaders = listOf("*")
        configuration.exposedHeaders = listOf("Authorization")
        configuration.allowCredentials = true
        configuration.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

}