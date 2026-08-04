package csh.back.domain.member.entity

import csh.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Duration
import java.time.LocalDateTime

@Entity
@Table(
    name = "members",
    // provider+providerId 조합으로 소셜 회원을 식별하므로 DB 레벨 중복 방지
    // LOCAL 회원은 providerId=null → MySQL/H2는 null을 unique 제약에서 서로 다른 값으로 취급해 복수 허용
    uniqueConstraints = [UniqueConstraint(columnNames = ["provider", "provider_id"])]
)
// @JvmOverloads: provider/providerId에 기본값이 있어 Kotlin끼리는 생략 가능하지만,
// Java는 기본값을 인식 못해 3인자 생성자가 없어 컴파일 에러 발생 → Java 테스트 코드 호환용
class Member @JvmOverloads constructor(
    val email: String,
    // 비밀번호 변경(재설정/현재 비번 확인 후 변경 등) 시 갱신되므로 var.
    // 원칙적으로 도메인 메서드(updatePassword) 사용, 부득이하게 직접 대입할 때는 반드시 encode 결과만 넣을 것.
    var password: String,
    val name: String,
    // 소셜 로그인 식별자 (A-lite 방식): 이메일 대신 provider+providerId 조합으로 소셜 회원 조회
    // email/password 컬럼을 nullable로 전환하지 않고, 카카오 가입 시 placeholder 값으로 채움
    val provider: String = "LOCAL",   // "LOCAL" | "KAKAO"
    val providerId: String? = null,   // 카카오 회원번호 (소셜 로그인만 사용, 일반 회원은 null)
) : BaseEntity() {

    // 회원가입 시 발급된 recovery code의 BCrypt 해시.
    //   - raw 코드는 가입 응답에 1회만 노출되고 서버에 저장하지 않음 → 이후 서버도 원본 모름.
    //   - nullable: 카카오 회원은 recovery code 없음(비번 자체가 없어 재설정 개념도 없음) + 마이그레이션 유예를 위해 허용.
    //   - length=60: BCrypt 해시 고정 길이
    @Column(name = "recovery_code_hash", nullable = true, length = 60)
    var recoveryCodeHash: String? = null
        protected set

    // 브루트포스 방어용 연속 실패 카운트 — 성공 또는 비밀번호 재설정 시 0으로 리셋
    @Column(name = "failed_login_count", nullable = false)
    var failedLoginCount: Int = 0
        protected set

    // 락 해제 시각. null 또는 과거면 로그인 가능
    @Column(name = "locked_until")
    var lockedUntil: LocalDateTime? = null
        protected set

    fun isLocked(now: LocalDateTime = LocalDateTime.now()): Boolean =
        lockedUntil?.isAfter(now) == true

    // 임계값 도달 시 lockedUntil 세팅. 임계값 미만이면 카운트만 증가.
    fun registerLoginFailure(threshold: Int, lockDuration: Duration, now: LocalDateTime = LocalDateTime.now()) {
        failedLoginCount += 1
        if (failedLoginCount >= threshold) {
            lockedUntil = now.plus(lockDuration)
        }
    }

    fun resetLoginFailures() {
        failedLoginCount = 0
        lockedUntil = null
    }

    // 비밀번호 재설정 성공 시 호출 — 락도 함께 해제 (정상 소유자 검증됨)
    fun updatePassword(newHashedPassword: String) {
        password = newHashedPassword
        resetLoginFailures()
    }

    // recovery code 최초 발급/재발급 시 호출 — 반드시 BCrypt 해시만 전달할 것 (raw 저장 금지)
    fun assignRecoveryCodeHash(newHash: String) {
        recoveryCodeHash = newHash
    }
}
