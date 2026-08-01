package csh.back.domain.member.repository

import csh.back.domain.member.entity.Member
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface MemberRepository : JpaRepository<Member, Long> {
    fun existsByEmail(email: String): Boolean
    fun findByEmail(email: String): Optional<Member>
    fun findByProviderAndProviderId(provider: String, providerId: String): Optional<Member>
}