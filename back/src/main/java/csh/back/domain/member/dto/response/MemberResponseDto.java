package csh.back.domain.member.dto.response;

import csh.back.domain.member.entity.Member;

// 회원가입 응답 DTO
public record MemberResponseDto(
        Long id,
        String email,
        String name
) {
    // 엔티티 -> DTO 변환
    public static MemberResponseDto from(Member member) {
        return new MemberResponseDto(
                member.getId(),
                member.getEmail(),
                member.getName()
        );
    }
}