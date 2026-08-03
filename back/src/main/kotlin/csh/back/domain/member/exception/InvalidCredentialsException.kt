package csh.back.domain.member.exception

// 이메일 존재 여부 노출 방지 — 이메일 미존재 / 비밀번호 불일치를 동일 예외로 통합.
// GlobalExceptionHandler에서 401 Unauthorized로 응답.
// remainingAttempts: 실제 회원의 비번 오류 시에만 채워짐 (락아웃 임계값까지 남은 시도 횟수).
//   미존재 이메일 케이스는 null이라 프론트에서 힌트가 노출되지 않음 — 최소한의 열거 방어.
// 기본 메시지를 생성자 기본값으로 둔 이유: 대부분의 호출부는 인자 없이 사용,
//   특수한 컨텍스트(예: 소셜 회원의 로컬 로그인 시도)에서만 다른 메시지 주입 여지를 남김.
class InvalidCredentialsException(
    val remainingAttempts: Int? = null,
    message: String = "이메일 또는 비밀번호가 올바르지 않습니다.",
) : RuntimeException(message)
