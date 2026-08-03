package csh.back.domain.member.service

import csh.back.domain.member.entity.Member
import csh.back.domain.member.entity.RefreshToken
import csh.back.domain.member.exception.InvalidPasswordException
import csh.back.domain.member.exception.KakaoMemberPasswordChangeException
import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.member.repository.RefreshTokenRepository
import csh.back.global.mail.EmailCooldownGuard
import csh.back.global.mail.MailService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.UUID

@ActiveProfiles("test")
@SpringBootTest
class ChangePasswordServiceTest {

    @Autowired lateinit var memberService: MemberService
    @Autowired lateinit var memberRepository: MemberRepository
    @Autowired lateinit var refreshTokenRepository: RefreshTokenRepository
    @Autowired lateinit var passwordEncoder: PasswordEncoder

    @MockitoBean lateinit var emailCooldownGuard: EmailCooldownGuard
    @MockitoBean lateinit var mailService: MailService

    private lateinit var testMember: Member

    companion object {
        private const val ORIGINAL_PASSWORD = "password123"
        private const val NEW_PASSWORD = "newpassword456"
        private const val TEST_EMAIL = "changepw-service@test.com"
    }

    @BeforeEach
    fun setUp() {
        memberRepository.findByEmail(TEST_EMAIL).ifPresent { m ->
            refreshTokenRepository.deleteAllByMember(m)
            memberRepository.delete(m)
        }
        testMember = memberRepository.save(
            Member(TEST_EMAIL, passwordEncoder.encode(ORIGINAL_PASSWORD)!!, "테스트유저")
        )
    }

    @AfterEach
    fun tearDown() {
        memberRepository.findByEmail(TEST_EMAIL).ifPresent { m ->
            refreshTokenRepository.deleteAllByMember(m)
            memberRepository.delete(m)
        }
    }

    @Test
    @DisplayName("t1: 현재 비밀번호가 맞으면 새 비밀번호로 변경된다")
    fun t1() {
        memberService.changePassword(testMember.id!!, ORIGINAL_PASSWORD, NEW_PASSWORD)

        val updated = memberRepository.findById(testMember.id!!).get()
        assertThat(passwordEncoder.matches(NEW_PASSWORD, updated.password)).isTrue()
        assertThat(passwordEncoder.matches(ORIGINAL_PASSWORD, updated.password)).isFalse()
    }

    @Test
    @DisplayName("t2: 현재 비밀번호가 틀리면 InvalidPasswordException이 발생한다")
    fun t2() {
        assertThatThrownBy {
            memberService.changePassword(testMember.id!!, "wrongPassword", NEW_PASSWORD)
        }.isInstanceOf(InvalidPasswordException::class.java)
            .hasMessage("현재 비밀번호가 일치하지 않습니다.")
    }

    @Test
    @DisplayName("t3: KAKAO 회원이면 KakaoMemberPasswordChangeException이 발생한다")
    fun t3() {
        val kakaoMember = memberRepository.save(
            Member(
                email = "kakao_changepw@triplog.local",
                password = passwordEncoder.encode(UUID.randomUUID().toString())!!,
                name = "카카오유저",
                provider = "KAKAO",
                providerId = "changepw-test-99999",
            )
        )
        try {
            assertThatThrownBy {
                memberService.changePassword(kakaoMember.id!!, "anyPassword", NEW_PASSWORD)
            }.isInstanceOf(KakaoMemberPasswordChangeException::class.java)
                .hasMessage("카카오 로그인 회원은 비밀번호를 변경할 수 없습니다.")
        } finally {
            memberRepository.delete(kakaoMember)
        }
    }

    @Test
    @DisplayName("t4: 변경 성공 시 해당 회원의 모든 RefreshToken이 삭제된다 (3개 기기 시뮬레이션)")
    fun t4() {
        repeat(3) { i ->
            refreshTokenRepository.save(RefreshToken(member = testMember, deviceId = "device-$i"))
        }
        val before = refreshTokenRepository.findAll().filter { it.member.id == testMember.id }
        assertThat(before).hasSize(3)

        memberService.changePassword(testMember.id!!, ORIGINAL_PASSWORD, NEW_PASSWORD)

        val after = refreshTokenRepository.findAll().filter { it.member.id == testMember.id }
        assertThat(after).isEmpty()
    }

    @Test
    @DisplayName("t5: 변경 후 새 비밀번호로 로그인이 가능하다")
    fun t5() {
        memberService.changePassword(testMember.id!!, ORIGINAL_PASSWORD, NEW_PASSWORD)

        assertThatCode {
            memberService.login(testMember.email, NEW_PASSWORD, null, "device-t5")
        }.doesNotThrowAnyException()
    }
}