package csh.back.domain.member.entity

import csh.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

// 비로그인 상태 비밀번호 재설정용 검증 토큰 (verificationToken).
//   - verify-code에서 (email, recovery code) 매칭 성공 시 발급
//   - apply에서 이 토큰을 검증 후 실제 비밀번호 갱신
//
// 저장 원칙:
//   - raw 토큰은 응답 body로만 노출, DB에는 SHA-256 해시만 (DB 유출 시 재사용 불가)
//   - 1회용: usedAt 마킹 후 재사용 차단
//   - 짧은 TTL (10분) — verify → apply 사이 시간만 커버
@Entity
@Table(
    name = "password_reset_tokens",
    // 특정 회원의 활성 토큰 정리 배치 등에 대비한 인덱스
    indexes = [Index(name = "idx_prt_member", columnList = "member_id")],
)
class PasswordResetToken(
    // fetch=LAZY: apply 흐름에서만 member를 참조하므로 지연 로딩으로 불필요 SELECT 회피
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    // SHA-256(raw token)의 hex 문자열 — 64자 고정
    // unique: 사실상 충돌 확률 0이지만 DB 레벨 방어망
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    val tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: LocalDateTime,

    // null이면 미사용. 마킹되면 재사용 불가
    @Column(name = "used_at")
    var usedAt: LocalDateTime? = null,
) : BaseEntity() {

    // 기본값 파라미터로 now 주입 — 테스트에서 시간 조작 편의
    fun isExpired(now: LocalDateTime = LocalDateTime.now()): Boolean = now.isAfter(expiresAt)

    fun isUsed(): Boolean = usedAt != null

    fun markUsed(now: LocalDateTime = LocalDateTime.now()) {
        usedAt = now
    }
}
