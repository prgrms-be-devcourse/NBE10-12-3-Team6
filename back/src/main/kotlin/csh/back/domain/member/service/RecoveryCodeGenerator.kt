package csh.back.domain.member.service

import org.springframework.stereotype.Component
import java.security.SecureRandom

// 회원가입 시 발급되는 recovery code 생성기.
// 규칙:
//   - 길이 6, 대문자 A-Z + 숫자 0-9 (36진법)
//   - 첫 자리는 0이 아님 (사용자 요구사항 — 시각적으로 앞자리 0으로 시작하지 않게)
//   - SecureRandom: 일반 Random과 달리 암호학적으로 예측 불가능한 시드 사용 →
//     같은 시각에 두 사용자가 가입해도 다른 코드가 발급될 확률 매우 높음
//
// 왜 @Component 인가:
//   - 상태(SecureRandom 인스턴스)를 재사용해 성능 손실 방지 (매 요청마다 새로 만들지 않음)
//   - 테스트에서 mock으로 교체 가능 (deterministic 코드 반환하도록)
@Component
class RecoveryCodeGenerator {

    // SecureRandom은 thread-safe (Sun 구현 기준). 인스턴스 하나를 재사용.
    private val random = SecureRandom()

    companion object {
        // 첫 자리 후보: 0 제외 (사용자 요구). 나머지 5자리는 전체 36진법 문자.
        private const val FIRST_CHARS = "123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val REST_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val CODE_LENGTH = 6
    }

    fun generate(): String {
        val sb = StringBuilder(CODE_LENGTH)
        // 첫 자리: 0 제외 세트에서 선택
        sb.append(FIRST_CHARS[random.nextInt(FIRST_CHARS.length)])
        // 나머지 5자리: 전체 세트
        repeat(CODE_LENGTH - 1) {
            sb.append(REST_CHARS[random.nextInt(REST_CHARS.length)])
        }
        return sb.toString()
    }
}
