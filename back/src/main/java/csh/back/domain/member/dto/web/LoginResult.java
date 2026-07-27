package csh.back.domain.member.dto.web;

import csh.back.domain.member.dto.response.LoginResponseDto;

// 로그인 결과 (사용자 정보 + 토큰을 컨트롤러에 전달하기 위한 내부 타입)
public record LoginResult(
        LoginResponseDto userInfo,
        String accessToken,
        String refreshToken
) {}