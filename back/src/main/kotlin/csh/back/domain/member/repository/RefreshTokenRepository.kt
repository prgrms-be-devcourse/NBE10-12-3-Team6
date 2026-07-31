package csh.back.domain.member.repository

import csh.back.domain.member.entity.Member
import csh.back.domain.member.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    // member를 JOIN FETCH해서 필터에서 lazy loading 없이 안전하게 접근
    @Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.member WHERE rt.token = :token")
    fun findByTokenWithMember(token: String): Optional<RefreshToken>

    // derived delete는 SimpleJpaRepository의 @Transactional을 상속받지 않아 명시 필요
    @Transactional
    fun deleteByToken(token: String)

    // 전체 기기 로그아웃용 — 현재 미사용, 향후 "모든 기기 로그아웃" API에서 호출
    @Transactional
    fun deleteAllByMember(member: Member)
}
