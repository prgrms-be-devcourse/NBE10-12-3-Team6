package csh.back.domain.member.service

import csh.back.domain.member.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

// 실패 카운트는 REQUIRES_NEW로 별도 트랜잭션에서 커밋해야 함 —
// login() 트랜잭션이 InvalidCredentialsException으로 rollback되어도 카운트는 유지되어야 하기 때문
@Service
class LoginAttemptTracker(
    private val memberRepository: MemberRepository,
) {

    companion object {
        // 5회 실패 → 5분 락. 정책 변경 시 여기만 조정
        const val FAILURE_THRESHOLD = 5
        val LOCK_DURATION: Duration = Duration.ofMinutes(5)
    }

    // REQUIRES_NEW: 호출한 login() 트랜잭션이 뒤이어 rollback되어도 이 update는 커밋됨.
    //   Spring AOP 특성상 self-invocation(같은 bean 안에서 호출)이면 어드바이스가 안 걸리므로
    //   반드시 별도 @Service bean으로 분리해서 외부 호출로 트리거해야 함.
    // orElse(null) + ?: return: 회원이 삭제된 극단 상황에서 NPE 대신 조용히 종료
    // dirty checking: JPA가 트랜잭션 커밋 시점에 엔티티 변경분을 자동 감지해 UPDATE 발행하므로
    //   member.registerLoginFailure(...)만 호출하면 별도 save() 불필요
    // 반환값: 실패 등록 후의 누적 카운트 (호출자가 "남은 시도" 계산에 사용). 회원 미존재 시 null.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordFailure(memberId: Long): Int? {
        val member = memberRepository.findById(memberId).orElse(null) ?: return null
        member.registerLoginFailure(FAILURE_THRESHOLD, LOCK_DURATION)
        return member.failedLoginCount
    }
}
