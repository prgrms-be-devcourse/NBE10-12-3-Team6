package csh.back.domain.trip.timeline.repository;

import csh.back.domain.trip.timeline.entity.Timeline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TimelineRepository extends JpaRepository<Timeline, Long> {
    List<Timeline> findAllByTripGroupId(Long tripId);

    //특정 여행 모임의 특정 일차 타임라인 목록을 시작 시간 기준으로 조회
    @Query("select t from Timeline t left join fetch t.tripWishPlace where t.tripGroup.id = :tripId and t.dayNumber = :dayNumber order by t.startTime asc")
    List<Timeline> findByTripGroupIdAndDayNumberOrderByStartTimeAsc(Long tripId, Long dayNumber);

    //수정, 삭제하려는 타임라인이 해당 여행 모임에 속하는지 확인하면서 조회
    Optional<Timeline> findByIdAndTripGroupId(Long timelineId, Long tripId);

    //같은 여행 모임, 같은 일차에 겹치는 시간 구간 개수 조회
    @Query("""
        select count(t)
        from Timeline t
        where t.tripGroup.id = :tripId
          and t.dayNumber = :dayNumber
          and t.startTime < :endTime
          and t.endTime > :startTime
        """)
    long countOverlappingTimeline(
            @Param("tripId") Long tripId,
            @Param("dayNumber") Long dayNumber,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    //수정 시 자기 자신을 제외한 겹치는 시간 구간 개수 조회
    @Query("""
        select count(t)
        from Timeline t
        where t.tripGroup.id = :tripId
          and t.dayNumber = :dayNumber
          and t.id <> :timelineId
          and t.startTime < :endTime
          and t.endTime > :startTime
        """)
    long countOverlappingTimelineExceptSelf(
            @Param("tripId") Long tripId,
            @Param("dayNumber") Long dayNumber,
            @Param("timelineId") Long timelineId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    @Query("SELECT vu.dayNumber, COUNT(vu) FROM Timeline vu WHERE vu.tripGroup.id = :tripId GROUP BY vu.dayNumber")
    List<Object[]> countGroupByDayNumberId(Long tripId);

    @Query("select t from Timeline t " +
            "left join fetch t.tripWishPlace " +
            "where t.tripGroup.id = :tripId and t.dayNumber = :dayNumber " +
            "order by t.startTime asc")
    List<Timeline> findByTripAndDateSorted(Long tripId, Long dayNumber);

    @Query("""
        SELECT t FROM Timeline t
        WHERE t.tripGroup.id = :tripId
        ORDER BY t.startTime ASC
        """)
    List<Timeline> findByTripGroupIdSorted(Long tripId);
}
