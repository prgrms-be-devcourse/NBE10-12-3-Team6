package csh.back.global.jwt

import io.jsonwebtoken.ExpiredJwtException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class JwtUtilTest {

    private val jwtUtil = JwtUtil(
        secret = "this-is-a-very-long-secret-key-for-jwt-signing-minimum-256-bits",
        accessExpiration = 1800000,
    )

    @Test
    @DisplayName("토큰 생성 후 이메일과 회원 ID를 추출할 수 있다")
    fun t1() {
        val token = jwtUtil.generateAccessToken(1L, "test@test.com")

        assertEquals("test@test.com", jwtUtil.getEmail(token))
        assertEquals(1L, jwtUtil.getMemberId(token))
    }

    @Test
    @DisplayName("정상 토큰은 유효하다")
    fun t2() {
        val token = jwtUtil.generateAccessToken(1L, "test@test.com")

        assertTrue(jwtUtil.isValid(token))
    }

    @Test
    @DisplayName("위조된 토큰은 유효하지 않다")
    fun t3() {
        val token = jwtUtil.generateAccessToken(1L, "test@test.com")
        val forgedToken = token.substring(0, token.length - 1) + if (token.last() == 'A') 'B' else 'A'

        assertFalse(jwtUtil.isValid(forgedToken))
    }

    @Test
    @DisplayName("만료된 토큰은 유효하지 않다")
    fun t4() {
        val expiredJwtUtil = JwtUtil(
            secret = "this-is-a-very-long-secret-key-for-jwt-signing-minimum-256-bits",
            accessExpiration = -1000,
        )
        val expiredToken = expiredJwtUtil.generateAccessToken(1L, "test@test.com")

        assertFalse(expiredJwtUtil.isValid(expiredToken))
    }

    @Test
    @DisplayName("만료된 토큰을 파싱하면 예외가 발생한다")
    fun t5() {
        val expiredJwtUtil = JwtUtil(
            secret = "this-is-a-very-long-secret-key-for-jwt-signing-minimum-256-bits",
            accessExpiration = -1000,
        )
        val expiredToken = expiredJwtUtil.generateAccessToken(1L, "test@test.com")

        assertThrows(ExpiredJwtException::class.java) {
            expiredJwtUtil.parseClaims(expiredToken)
        }
    }
}