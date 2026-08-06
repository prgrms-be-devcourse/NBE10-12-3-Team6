package csh.back.domain.member.service

import csh.back.domain.member.entity.PasswordResetToken
import csh.back.domain.member.exception.InvalidResetCredentialsException
import csh.back.domain.member.exception.InvalidResetTokenException
import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.member.repository.PasswordResetTokenRepository
import csh.back.domain.member.repository.RefreshTokenRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.LocalDateTime
import java.util.Base64

// 비로그인 상태 비밀번호 재설정 서비스 (2단계).
//
// 흐름:
//   1) verifyCode(email, code) — 회원가입 시 발급된 recovery code와 매칭 확인 → verificationToken 발급
//   2) apply(verificationToken, newPassword) — 토큰 검증 후 비밀번호 갱신 + 전체 세션 무효화
//
// 로그인 상태의 비밀번호 변경은 별개(MemberService.changePassword) — 현재 비번을 알고 있는 사용자용.
// 클래스 레벨 @Transactional(readOnly=true): 쓰기 메서드는 개별로 @Transactional 오버라이드
@Service
@Transactional(readOnly = true)
class PasswordResetService(
    private val memberRepository: MemberRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    companion object {
        private const val TOKEN_BYTES = 32                              // 256비트 → 브루트포스 사실상 불가
        // verify 직후 곧바로 apply 하도록 짧게 잡음 — 유저가 새 비번 입력하는 시간 정도
        val VERIFICATION_TOKEN_TTL: Duration = Duration.ofMinutes(10)
    }

    // 1단계: 이메일 + recovery code 조합 검증 후 verificationToken 발급.
    // 계정 열거 방지 위해 (이메일 미존재 / code 불일치 / recovery code 미보유) 모두 동일 예외로 통합.
    @Transactional
    fun verifyCode(email: String, rawCode: String): String {
        val member = memberRepository.findByEmail(email).orElse(null)
            ?: throw InvalidResetCredentialsException()

        // 카카오 회원 등 recovery code가 없는 회원은 이 경로로 재설정 불가
        val storedHash = member.recoveryCodeHash
            ?: throw InvalidResetCredentialsException()

        // BCrypt matches — 회원가입 때 저장한 해시와 사용자가 입력한 raw code 비교
        if (!passwordEncoder.matches(rawCode, storedHash)) {
            throw InvalidResetCredentialsException()
        }

        // 검증 통과 → verificationToken 발급 (SHA-256 해시만 DB 저장, raw는 응답에만 노출)
        val rawToken = generateRawToken()
        passwordResetTokenRepository.save(
            PasswordResetToken(
                member = member,
                tokenHash = sha256Hex(rawToken),
                expiresAt = LocalDateTime.now().plus(VERIFICATION_TOKEN_TTL),
            )
        )
        return rawToken
    }

    // 2단계: verify-code에서 받은 raw verificationToken으로 재설정 확정.
    // 토큰은 1회용 + 만료 검증 후, 성공 시:
    //   1) 새 비번 저장 (Member.updatePassword가 내부에서 실패 카운트/락도 자동 해제)
    //   2) 토큰을 사용 처리(재사용 차단)
    //   3) 해당 회원의 모든 refresh token 삭제 (탈취 시나리오 대비 전체 세션 무효화)
    @Transactional
    fun apply(verificationToken: String, newPassword: String) {
        val token = loadValidToken(verificationToken)

        // PasswordEncoder.encode는 Spring Framework 7.x에서 @Nullable로 선언되어 !! 필요
        token.member.updatePassword(passwordEncoder.encode(newPassword)!!)
        token.markUsed()
        refreshTokenRepository.deleteAllByMember(token.member)
    }

    // 존재 / 만료 / 재사용 여부를 한 곳에서 판정. 어느 케이스든 동일 예외로 통합
    private fun loadValidToken(rawToken: String): PasswordResetToken {
        val tokenHash = sha256Hex(rawToken)
        val token = passwordResetTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow { InvalidResetTokenException() }

        if (token.isUsed() || token.isExpired()) throw InvalidResetTokenException()
        return token
    }

    // URL-safe base64 (padding 제거) — 응답 body에 담기지만 안전한 인코딩 유지
    // SecureRandom: 일반 Random과 달리 암호학적으로 예측 불가능한 시드 사용
    private fun generateRawToken(): String {
        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    // MessageDigest는 thread-unsafe라 요청마다 새 인스턴스 생성 (짧은 문자열이라 오버헤드 미미)
    // "%02x".format(it): Byte 하나를 2자리 소문자 hex로 포맷 → SHA-256(32B) → 64자
    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
