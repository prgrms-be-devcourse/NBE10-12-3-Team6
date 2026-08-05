package csh.back.domain.member.service

import csh.back.domain.member.entity.Member
import csh.back.domain.member.entity.RefreshToken
import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.member.repository.RefreshTokenRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@ActiveProfiles("test")
@SpringBootTest
class MemberServiceRotateRefreshTokenConcurrencyTest {

    @Autowired lateinit var memberService: MemberService
    @Autowired lateinit var memberRepository: MemberRepository
    @Autowired lateinit var refreshTokenRepository: RefreshTokenRepository

    private lateinit var testMember: Member
    private lateinit var oldRefreshToken: RefreshToken

    companion object {
        private const val TEST_EMAIL = "rotate-concurrency@test.com"
        private const val DEVICE_ID = "rotate-concurrency-device"
        private const val THREAD_COUNT = 5
    }

    @BeforeEach
    fun setUp() {
        memberRepository.findByEmail(TEST_EMAIL).ifPresent { m ->
            refreshTokenRepository.deleteAllByMember(m)
            memberRepository.delete(m)
        }
        testMember = memberRepository.save(Member(TEST_EMAIL, "pw", "동시성유저"))
        oldRefreshToken = refreshTokenRepository.save(RefreshToken(member = testMember, deviceId = DEVICE_ID))
    }

    @AfterEach
    fun tearDown() {
        memberRepository.findByEmail(TEST_EMAIL).ifPresent { m ->
            refreshTokenRepository.deleteAllByMember(m)
            memberRepository.delete(m)
        }
    }

    // 같은 (member, deviceId)로 회전 요청이 동시에 몰리는 상황(예: accessToken 만료 직후 병렬 API 호출)을 재현한다.
    // 이전에는 delete→insert 경합으로 늦은 요청이 유니크 제약(member_id, device_id) 위반 예외를 던졌다.
    @Test
    @DisplayName("같은 (member, deviceId)로 refreshToken 회전이 동시에 여러 번 들어와도 예외 없이 처리되고 행이 1개만 남는다")
    fun concurrentRotateOnSameDeviceDoesNotThrow() {
        val executor = Executors.newFixedThreadPool(THREAD_COUNT)
        val ready = CountDownLatch(THREAD_COUNT)
        val start = CountDownLatch(1)
        val done = CountDownLatch(THREAD_COUNT)
        val errors = mutableListOf<Throwable>()
        val results = mutableListOf<RefreshToken>()

        repeat(THREAD_COUNT) {
            executor.submit {
                ready.countDown()
                start.await()
                try {
                    val result = memberService.rotateRefreshToken(oldRefreshToken)
                    synchronized(results) { results.add(result) }
                } catch (e: Throwable) {
                    synchronized(errors) { errors.add(e) }
                } finally {
                    done.countDown()
                }
            }
        }

        ready.await()
        start.countDown()
        done.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(errors).isEmpty()
        assertThat(results).hasSize(THREAD_COUNT)

        val remaining = refreshTokenRepository.findAll().filter { it.member.id == testMember.id }
        assertThat(remaining).hasSize(1)

        // 경합에서 진 요청도 방금 커밋된 최신 토큰을 재조회해 반환하므로, 모든 결과가 DB에 남은 토큰과 일치해야 한다
        assertThat(results.map { it.token }.toSet()).containsExactly(remaining.first().token)
    }
}
