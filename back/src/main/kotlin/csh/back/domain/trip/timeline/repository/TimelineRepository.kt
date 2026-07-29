package csh.back.domain.trip.timeline.repository

import csh.back.domain.trip.timeline.entity.Timeline
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.Optional

interface TimelineRepository : JpaRepository<Timeline, Long> {

    fun findAllByTripGroupId(tripId: Long): List<Timeline>

    @Query(
        """
        select t
        from Timeline t
        left join fetch t.tripWishPlace
        where t.tripGroup.id = :tripId
          and t.dayNumber = :dayNumber
        order by t.startTime asc
        """,
    )
    fun findByTripGroupIdAndDayNumberOrderByStartTimeAsc(
        @Param("tripId") tripId: Long,
        @Param("dayNumber") dayNumber: Long,
    ): List<Timeline>

    fun findByIdAndTripGroupId(timelineId: Long, tripId: Long): Optional<Timeline>

    @Query(
        """
        select count(t)
        from Timeline t
        where t.tripGroup.id = :tripId
          and t.dayNumber = :dayNumber
          and t.startTime < :endTime
          and t.endTime > :startTime
        """,
    )
    fun countOverlappingTimeline(
        @Param("tripId") tripId: Long,
        @Param("dayNumber") dayNumber: Long,
        @Param("startTime") startTime: LocalDateTime,
        @Param("endTime") endTime: LocalDateTime,
    ): Long

    @Query(
        """
        select count(t)
        from Timeline t
        where t.tripGroup.id = :tripId
          and t.dayNumber = :dayNumber
          and t.id <> :timelineId
          and t.startTime < :endTime
          and t.endTime > :startTime
        """,
    )
    fun countOverlappingTimelineExceptSelf(
        @Param("tripId") tripId: Long,
        @Param("dayNumber") dayNumber: Long,
        @Param("timelineId") timelineId: Long,
        @Param("startTime") startTime: LocalDateTime,
        @Param("endTime") endTime: LocalDateTime,
    ): Long

    @Query(
        """
        select t.dayNumber, count(t)
        from Timeline t
        where t.tripGroup.id = :tripId
        group by t.dayNumber
        """,
    )
    fun countGroupByDayNumberId(
        @Param("tripId") tripId: Long,
    ): List<Array<Any>>

    @Query(
        """
        select t
        from Timeline t
        left join fetch t.tripWishPlace
        where t.tripGroup.id = :tripId
          and t.dayNumber = :dayNumber
        order by t.startTime asc
        """,
    )
    fun findByTripAndDateSorted(
        @Param("tripId") tripId: Long,
        @Param("dayNumber") dayNumber: Long,
    ): List<Timeline>

    @Query(
        """
        select t
        from Timeline t
        where t.tripGroup.id = :tripId
        order by t.startTime asc
        """,
    )
    fun findByTripGroupIdSorted(
        @Param("tripId") tripId: Long,
    ): List<Timeline>
}
