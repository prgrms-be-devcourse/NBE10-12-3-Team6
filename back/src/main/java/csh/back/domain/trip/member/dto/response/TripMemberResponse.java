package csh.back.domain.trip.member.dto.response;

import csh.back.domain.trip.member.entity.TripMember;

public record TripMemberResponse(
		Long memberId,
		String name,
		boolean admin
) {
	public static TripMemberResponse from(TripMember tripMember) {
		return new TripMemberResponse(
				tripMember.getMember().getId(),
				tripMember.getMember().getName(),
				tripMember.isAdmin()
		);
	}
}
