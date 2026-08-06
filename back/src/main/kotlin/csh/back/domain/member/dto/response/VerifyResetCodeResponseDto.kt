package csh.back.domain.member.dto.response

// verify-code 성공 시 프론트로 전달되는 짧은 유효기간 토큰
// 프론트는 이 토큰을 메모리에만 보관해 apply 호출 시 함께 전송 (URL/localStorage 노출 안 함)
data class VerifyResetCodeResponseDto(
    val verificationToken: String,
)
