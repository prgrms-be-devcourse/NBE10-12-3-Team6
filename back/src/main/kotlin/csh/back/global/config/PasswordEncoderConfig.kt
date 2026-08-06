package csh.back.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

// SecurityConfig에서 분리: SecurityConfig → KakaoOAuth2SuccessHandler → MemberService → PasswordEncoder → SecurityConfig 순환 참조 방지
@Configuration
class PasswordEncoderConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}