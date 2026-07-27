package csh.back.domain.trip.member.repository;

import csh.back.domain.trip.member.entity.TripMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripMemberRepository extends JpaRepository<TripMember, Long> {

	//해당 사용자가 특정 여행 모임의 멤버인지 확인
	boolean existsByTripGroupIdAndMemberId(Long tripGroupId, Long memberId);

	//해당 사용자가 특정 여행 모임의 방장인지 확인
	boolean existsByTripGroupIdAndMemberIdAndIsAdminTrue(Long tripGroupId, Long memberId);

	Optional<TripMember> findByMemberIdAndTripGroupId(Long memberId, Long tripGroupId);

	Optional<TripMember> findByMemberId(Long memberId);

	List<TripMember> findByTripGroupId(Long tripGroupId);
}