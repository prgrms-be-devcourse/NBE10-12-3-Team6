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

    @Transactional
    fun deleteByToken(token: String)

    @Transactional
    fun deleteAllByMember(member: Member)
}
