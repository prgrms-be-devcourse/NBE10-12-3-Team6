package csh.back.domain.member.entity

import csh.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

// Member 당 1개였던 refreshToken 필드를 분리 — 기기마다 독립 토큰으로 멀티 디바이스 지원
@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @Column(unique = true, nullable = false)
    val token: String = UUID.randomUUID().toString(),

    // 쿠키 maxAge(7일)와 일치시켜 쿠키·DB 만료 불일치 방지
    @Column(nullable = false)
    val expiresAt: LocalDateTime = LocalDateTime.now().plusDays(7),

    // #28 새 기기 로그인 알림에서 기기 구분에 사용 — 지금은 저장만, 판단 로직은 #28에서 추가
    @Column
    val userAgent: String? = null,
) : BaseEntity() {
    fun isExpired(): Boolean = LocalDateTime.now().isAfter(expiresAt)
}
