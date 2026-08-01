package csh.back.domain.member.entity

import csh.back.domain.member.repository.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest
class MemberUniqueConstraintTest {

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @AfterEach
    fun cleanup() {
        memberRepository.deleteAll(
            memberRepository.findAll().filter { it.email.endsWith("@constrainttest.local") }
        )
    }

    @Test
    @DisplayName("t1 - 같은 (provider, providerId) 조합 중복 저장 시 unique 제약 위반")
    fun t1() {
        memberRepository.saveAndFlush(
            Member("a@constrainttest.local", "pw", "A", "KAKAO", "same-kakao-id")
        )

        assertThrows<DataIntegrityViolationException> {
            memberRepository.saveAndFlush(
                Member("b@constrainttest.local", "pw", "B", "KAKAO", "same-kakao-id")
            )
        }
    }

    @Test
    @DisplayName("t2 - provider가 다르면 providerId가 같아도 정상 저장 (소셜 플랫폼 간 독립)")
    fun t2() {
        memberRepository.save(Member("a@constrainttest.local", "pw", "A", "KAKAO", "same-id"))
        memberRepository.save(Member("b@constrainttest.local", "pw", "B", "GOOGLE", "same-id"))

        val saved = memberRepository.findAll().filter { it.providerId == "same-id" }
        assertThat(saved).hasSize(2)
    }

    @Test
    @DisplayName("t3 - LOCAL 회원 여러 명이 providerId=null이어도 정상 저장 (null은 unique 제약에서 서로 다른 값)")
    fun t3() {
        memberRepository.save(Member("a@constrainttest.local", "pw", "A", "LOCAL", null))
        memberRepository.save(Member("b@constrainttest.local", "pw", "B", "LOCAL", null))
        memberRepository.save(Member("c@constrainttest.local", "pw", "C", "LOCAL", null))

        val localMembers = memberRepository.findAll()
            .filter { it.email.endsWith("@constrainttest.local") && it.provider == "LOCAL" }
        assertThat(localMembers).hasSize(3)
    }
}