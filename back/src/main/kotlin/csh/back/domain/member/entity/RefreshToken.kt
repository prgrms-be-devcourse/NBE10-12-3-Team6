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

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @Column(unique = true, nullable = false)
    val token: String = UUID.randomUUID().toString(),

    @Column(nullable = false)
    val expiresAt: LocalDateTime = LocalDateTime.now().plusDays(7),

    @Column
    val userAgent: String? = null,
) : BaseEntity() {
    fun isExpired(): Boolean = LocalDateTime.now().isAfter(expiresAt)
}
