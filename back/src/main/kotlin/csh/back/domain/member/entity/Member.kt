package csh.back.domain.member.entity

import csh.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "members")
class Member(
    val email: String,
    val password: String,
    val name: String,
    val provider: String = "LOCAL",
    val providerId: String? = null,
) : BaseEntity() {

    // null 대신 UUID로 초기화해 unique 제약 조건을 유지하면서 로그아웃 시 무효화 처리
    @Column(unique = true)
    var refreshToken: String = UUID.randomUUID().toString()

    // 로그아웃 시 새 UUID로 교체해 기존 토큰을 무효화 (DB에서 조회 불가 상태로 만듦)
    fun invalidateRefreshToken() {
        refreshToken = UUID.randomUUID().toString()
    }
}