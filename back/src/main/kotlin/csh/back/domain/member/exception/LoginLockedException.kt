package csh.back.domain.member.exception

// 연속 로그인 실패로 계정이 일시적으로 잠긴 상태.
// retryAfterSeconds를 필드로 보유해 클라이언트에서 재시도 카운트다운 UI 등에 활용 가능.
// GlobalExceptionHandler에서 429 Too Many Requests로 응답 (rate-limit 계열 표준 코드).
class LoginLockedException(val retryAfterSeconds: Long) :
    RuntimeException("연속 로그인 실패로 계정이 잠겼습니다. ${retryAfterSeconds}초 후 다시 시도해 주세요.")
