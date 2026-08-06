package csh.back.domain.member.dto.web

import csh.back.domain.member.dto.response.LoginResponseDto

// Service → Controller 내부 전달용 (JSON 직렬화 대상 아님)
data class LoginResult(
    val userInfo: LoginResponseDto,
    val accessToken: String,
    val refreshToken: String,
)