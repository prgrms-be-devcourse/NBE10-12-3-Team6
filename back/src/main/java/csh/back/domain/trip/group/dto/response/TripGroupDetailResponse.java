package csh.back.domain.trip.group.dto.response;

import csh.back.domain.trip.group.entity.TripGroup;
import csh.back.domain.trip.member.dto.response.TripMemberResponse;

import java.time.LocalDate;
import java.util.List;

public record TripGroupDetailResponse(
		Long id,
		String name,
		Long ownerId,
		String region,
		String joinCode,
		int nights,
		LocalDate startDate,
		LocalDate endDate,
		List<TripMemberResponse> members
){
	public static TripGroupDetailResponse from(TripGroup tripGroup, List<TripMemberResponse> members) {
		return new TripGroupDetailResponse(
				tripGroup.getId(),
				tripGroup.getName(),
				tripGroup.getOwner().getId(),
				tripGroup.getRegion(),
				tripGroup.getJoinCode(),
				tripGroup.getNights(),
				tripGroup.getStartDate(),
				tripGroup.getEndDate(),
				members
		);
	}
}
