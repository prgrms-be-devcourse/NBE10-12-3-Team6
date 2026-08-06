package csh.back.domain.member.entity

import csh.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime
import java.util.UUID

// Member 당 1개였던 refreshToken 필드를 분리 — 기기마다 독립 토큰으로 멀티 디바이스 지원
@Entity
@Table(
    name = "refresh_tokens",
    // device_id 단독 unique 불가 — 공용 기기에서 여러 계정이 로그인 가능하므로 (member_id, device_id) 조합만 유일
    uniqueConstraints = [UniqueConstraint(columnNames = ["member_id", "device_id"])]
)
class RefreshToken(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    // rotate()로만 갱신 — 생성자 프로퍼티는 커스텀 접근자를 붙일 수 없어 var로 선언
    @Column(unique = true, nullable = false)
    var token: String = UUID.randomUUID().toString(),

    // 쿠키 maxAge(7일)와 일치시켜 쿠키·DB 만료 불일치 방지
    @Column(nullable = false)
    var expiresAt: LocalDateTime = LocalDateTime.now().plusDays(7),

    // #28 새 기기 로그인 알림에서 기기 구분에 사용 — 지금은 저장만, 판단 로직은 #28에서 추가
    @Column
    val userAgent: String? = null,

    // 로그아웃 후 재로그인해도 동일 기기 식별 가능 (로그아웃 시 device_id 쿠키는 유지됨)
    @Column(name = "device_id", nullable = false)
    val deviceId: String,
) : BaseEntity() {
    fun isExpired(): Boolean = LocalDateTime.now().isAfter(expiresAt)

    // delete+insert 대신 기존 행을 그대로 갱신 — 동시 회전 시 유니크 제약 위반 예외 자체가 발생하지 않는다
    fun rotate() {
        token = UUID.randomUUID().toString()
        expiresAt = LocalDateTime.now().plusDays(7)
    }
}
