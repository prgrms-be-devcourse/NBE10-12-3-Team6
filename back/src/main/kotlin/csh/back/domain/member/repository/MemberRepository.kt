package csh.back.domain.member.repository

import csh.back.domain.member.entity.Member
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface MemberRepository : JpaRepository<Member, Long> {
    fun existsByEmail(email: String): Boolean
    fun findByEmail(email: String): Optional<Member>
    // 토큰 갱신(refresh) 요청 시 refreshToken으로 회원을 조회하기 위해 사용
    fun findByRefreshToken(refreshToken: String): Optional<Member>
}