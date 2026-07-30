package csh.back.global.config

import csh.back.domain.member.repository.MemberRepository
import csh.back.global.jwt.JwtAuthenticationFilter
import csh.back.global.jwt.JwtUtil
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtUtil: JwtUtil,
    private val memberRepository: MemberRepository
) {

    @Value("\${cors.allowed-origins}")
    private lateinit var allowedOrigins: List<String>

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { cors -> cors.configurationSource(corsConfigurationSource()) }
            .csrf { csrf -> csrf.disable() }
            // JWT를 사용하므로 서버에 세션을 생성하지 않음
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
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
                    "/uploadedimages/**",
                    "/actuator/prometheus"
                ).permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // 그 외 모든 요청은 JWT 필터를 거치되 인증 강제하지 않음
                    .anyRequest().authenticated()
            }
            // Spring의 기본 로그인 필터 앞에 JWT 필터를 끼워 넣음
            .addFilterBefore(JwtAuthenticationFilter(jwtUtil, memberRepository), UsernamePasswordAuthenticationFilter::class.java)

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

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }
}