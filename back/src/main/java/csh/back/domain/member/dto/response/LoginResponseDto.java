package csh.back.domain.member.dto.response;

import csh.back.domain.member.entity.Member;

// 로그인 응답 DTO - 토큰은 httpOnly 쿠키(Set-Cookie)로 별도 전달됨 -> 토큰을 Authorization 헤더가 아니라 Set-Cookie로 전달
public record LoginResponseDto(
        Long id,
        String email,
        String name
) {
    public static LoginResponseDto from(Member member) {
        return new LoginResponseDto(
                member.getId(),
                member.getEmail(),
                member.getName()
        );
    }
}