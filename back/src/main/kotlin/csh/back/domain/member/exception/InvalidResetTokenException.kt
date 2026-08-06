package csh.back.domain.member.exception

// apply 단계에서 verificationToken이 유효하지 않을 때 (존재하지 않음 / 만료 / 이미 사용됨) 통합 예외.
// 세 케이스를 하나의 메시지로 통합해 정보 유출 최소화. GlobalExceptionHandler에서 400 응답.
class InvalidResetTokenException(
    message: String = "검증 정보가 유효하지 않거나 만료되었습니다. 처음부터 다시 시도해 주세요.",
) : RuntimeException(message)
