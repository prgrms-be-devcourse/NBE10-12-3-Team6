package csh.back.global.jwt

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtUtil(
    @Value("\${jwt.secret}") secret: String,
    @Value("\${jwt.access-expiration}") private val accessExpiration: Long
) {
    private val secretKey: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

    // Access Token 생성 (만료시간: 30분)
    fun generateAccessToken(memberId: Long, email: String): String {
        return buildToken(memberId, email, accessExpiration)
    }

    // 토큰 생성 공통 로직
    private fun buildToken(memberId: Long, email: String, expiration: Long): String {
        val now = Date()
        return Jwts.builder()
            .subject(email)
            .claim("memberId", memberId)
            .issuedAt(now)
            .expiration(Date(now.time + expiration))
            .signWith(secretKey)
            .compact()
    }

    // 토큰에서 클레임(데이터) 추출 - 만료/위조된 토큰이면 예외 발생
    fun parseClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    // 토큰 유효성 검증 (만료, 위조 여부 확인)
    fun isValid(token: String): Boolean {
        return try {
            parseClaims(token)
            true
        } catch (e: Exception) {
            false
        }
    }

    // 토큰에서 이메일 추출
    fun getEmail(token: String): String {
        return parseClaims(token).subject
    }

    // 토큰에서 회원 ID 추출 (Integer로 역직렬화될 수 있어 Number로 변환)
    fun getMemberId(token: String): Long {
        return (parseClaims(token)["memberId"] as Number).toLong()
    }
}