package csh.back.global.dto

import com.fasterxml.jackson.annotation.JsonInclude

// nullable 필드는 값이 있을 때만 응답 body에 포함 — 기존 (statusCode, message)만 있는 응답과 shape 호환 유지
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val statusCode: Int,
    val message: String?,
    // 429 락아웃 응답에만 채워짐 — 프론트가 카운트다운 UI에 사용
    val retryAfterSeconds: Long? = null,
    // 401 자격증명 오류 응답에만 채워짐 (해당 이메일이 실제 회원일 때만) — 프론트가 "남은 시도 N회" 표시에 사용
    val remainingAttempts: Int? = null,
)
