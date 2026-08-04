package csh.back.domain.member.repository

import csh.back.domain.member.entity.PasswordResetToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, Long> {
    // Spring Data derived query — 메서드명으로 SELECT ... WHERE token_hash = ? 자동 생성
    // Optional 반환: 토큰 미존재는 정상 케이스(만료/오타)라 예외 대신 Optional로 전달, 서비스 계층에서 판정
    fun findByTokenHash(tokenHash: String): Optional<PasswordResetToken>
}
