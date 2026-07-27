package csh.back.domain.trip.member.dto.response;

import csh.back.domain.trip.member.entity.TripMember;

public record TripMemeberResponse (
		Long memberId,
		String name,
		boolean admin
) {
	public static TripMemeberResponse from(TripMember tripMember) {
		return new TripMemeberResponse(
				tripMember.getMember().getId(),
				tripMember.getMember().getName(),
				tripMember.isAdmin()
		);
	}
}
