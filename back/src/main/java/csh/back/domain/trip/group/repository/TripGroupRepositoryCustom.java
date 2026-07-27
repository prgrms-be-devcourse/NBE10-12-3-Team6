package csh.back.domain.trip.group.repository;

import csh.back.domain.trip.group.entity.TripGroup;

import java.util.List;

// 1. 커스텀 메서드를 선언
public interface TripGroupRepositoryCustom {
	List<TripGroup> findAllByMemberIdWithSearch(Long memberId, String keyword, String startDate);
}
