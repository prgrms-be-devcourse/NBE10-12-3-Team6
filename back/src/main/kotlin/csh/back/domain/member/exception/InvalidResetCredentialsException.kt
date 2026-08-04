package csh.back.domain.member.exception

// 재설정 verify-code 단계에서 (이메일 미존재 / recovery code 불일치 / recovery code 미보유) 통합 예외.
// 세 케이스를 구분하지 않는 이유: 이메일이나 코드 존재 여부가 노출되면 계정 열거(enumeration)에 취약.
// GlobalExceptionHandler에서 400 Bad Request로 응답.
class InvalidResetCredentialsException(
    message: String = "이메일 또는 코드가 올바르지 않습니다.",
) : RuntimeException(message)
