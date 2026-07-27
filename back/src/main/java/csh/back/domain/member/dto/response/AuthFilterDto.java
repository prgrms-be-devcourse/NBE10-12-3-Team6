package csh.back.domain.member.dto.response;

import csh.back.domain.member.entity.Member;

public record AuthFilterDto (
		Long id,
		String email
) {
	public static AuthFilterDto from(Member member) {
		return new AuthFilterDto(
				member.getId(),
				member.getEmail()
		);
	}
}
