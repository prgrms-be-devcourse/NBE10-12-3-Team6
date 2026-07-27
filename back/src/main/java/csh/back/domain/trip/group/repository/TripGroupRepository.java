package csh.back.domain.trip.group.repository;

import csh.back.domain.trip.group.entity.TripGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TripGroupRepository extends JpaRepository<TripGroup, Long>, TripGroupRepositoryCustom {
	List<TripGroup> findAllByOwnerIdOrderByStartDateDesc(Long memberId);

	@Query("SELECT tg FROM TripGroup tg JOIN TripMember tm ON tm.tripGroup = tg WHERE tm.member.id = :memberId ORDER BY tg.startDate DESC")
	List<TripGroup> findAllByMemberId(@Param("memberId") Long memberId);

	List<TripGroup> findAllByMemberIdWithSearch(@Param("memberId") Long memberId, String keyword, String startDate);

	boolean existsByJoinCode(String joinCode);
	Optional<TripGroup> findByJoinCode(String joinCode);

	//같은 여행 모임의 타임라인 수정 요청을 순차적으로 처리하기 위한 락 조회 메서드
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select tg from TripGroup tg where tg.id = :tripId")
	Optional<TripGroup> findByIdWithLock(@Param("tripId") Long tripId);
}
