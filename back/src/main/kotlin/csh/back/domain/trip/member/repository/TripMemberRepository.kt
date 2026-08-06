package csh.back.domain.trip.member.repository

import csh.back.domain.trip.member.entity.TripMember
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface TripMemberRepository : JpaRepository<TripMember, Long> {

    fun existsByTripGroupIdAndMemberId(tripGroupId: Long, memberId: Long): Boolean

    fun existsByTripGroupIdAndMemberIdAndIsAdminTrue(tripGroupId: Long, memberId: Long): Boolean

    fun findByMemberIdAndTripGroupId(memberId: Long, tripGroupId: Long): Optional<TripMember>

    fun findByMemberId(memberId: Long): Optional<TripMember>

    fun findByTripGroupId(tripGroupId: Long): List<TripMember>

    // 초대 시 이미 이 방의 멤버인 ID를 한 번에 조회 (배치)
    @Query("SELECT tm.member.id FROM TripMember tm WHERE tm.tripGroup.id = :tripGroupId AND tm.member.id IN :memberIds")
    fun findExistingMemberIds(
        @Param("tripGroupId") tripGroupId: Long,
        @Param("memberIds") memberIds: List<Long>,
    ): List<Long>

    // requesterId와 어떤 여행방이든 함께 속한 적 있는 회원 ID만 반환 (지난 메이트 + 현재 여행방 멤버).
    // presence 조회 시 프라이버시 스코프 필터로 사용.
    @Query(
        """
        SELECT DISTINCT other.member.id
        FROM TripMember me
        JOIN TripMember other ON other.tripGroup = me.tripGroup
        WHERE me.member.id = :requesterId
          AND other.member.id IN :memberIds
          AND other.member.id <> :requesterId
        """
    )
    fun findRelatedMemberIds(
        @Param("requesterId") requesterId: Long,
        @Param("memberIds") memberIds: List<Long>,
    ): List<Long>

    // 내가 참여했던 여행방의 다른 멤버들을, 함께한 여행 횟수/최근 시작일/최근 여행방 이름과 함께 최근순으로 반환.
    // Slice: hasNext만 알면 되는 무한 스크롤용 (Page와 달리 count 쿼리 안 함).
    //
    // latestGroupName은 상관 서브쿼리로 뽑는다:
    //   1) 이 mate와 :memberId가 함께 있었던 방들 중 startDate MAX를 구하고
    //   2) 그 date에 해당하는 방들 중 id MAX를 tie-breaker로 사용해 방 1개를 확정한 뒤
    //   3) 그 방의 name을 가져온다.
    // → GROUP BY 위반 없이 결정적으로 "가장 최근 방 이름"을 반환.
    @Query(
        """
        SELECT
            mate.member.id AS id,
            mate.member.name AS name,
            COUNT(DISTINCT mate.tripGroup.id) AS travelCount,
            MAX(mate.tripGroup.startDate) AS latestTravelDate,
            (SELECT tgLatest.name FROM TripGroup tgLatest
             WHERE tgLatest.id = (
                 SELECT MAX(tmL.tripGroup.id) FROM TripMember tmL
                 JOIN TripMember meL ON meL.tripGroup = tmL.tripGroup
                 WHERE meL.member.id = :memberId
                   AND tmL.member.id = mate.member.id
                   AND tmL.tripGroup.startDate = (
                       SELECT MAX(tmM.tripGroup.startDate) FROM TripMember tmM
                       JOIN TripMember meM ON meM.tripGroup = tmM.tripGroup
                       WHERE meM.member.id = :memberId
                         AND tmM.member.id = mate.member.id
                   )
             )) AS latestGroupName
        FROM TripMember me
        JOIN TripMember mate ON mate.tripGroup = me.tripGroup
        WHERE me.member.id = :memberId
          AND mate.member.id <> :memberId
        GROUP BY mate.member.id, mate.member.name
        ORDER BY MAX(mate.tripGroup.startDate) DESC, mate.member.name ASC, mate.member.id ASC
        """
    )
    fun findPastMatesByMemberId(
        @Param("memberId") memberId: Long,
        pageable: Pageable,
    ): Slice<PastMateProjection>

    // 이름 키워드로 지난 메이트 필터링 (대소문자 무시). latestGroupName 서브쿼리 구조는 위와 동일.
    @Query(
        """
        SELECT
            mate.member.id AS id,
            mate.member.name AS name,
            COUNT(DISTINCT mate.tripGroup.id) AS travelCount,
            MAX(mate.tripGroup.startDate) AS latestTravelDate,
            (SELECT tgLatest.name FROM TripGroup tgLatest
             WHERE tgLatest.id = (
                 SELECT MAX(tmL.tripGroup.id) FROM TripMember tmL
                 JOIN TripMember meL ON meL.tripGroup = tmL.tripGroup
                 WHERE meL.member.id = :memberId
                   AND tmL.member.id = mate.member.id
                   AND tmL.tripGroup.startDate = (
                       SELECT MAX(tmM.tripGroup.startDate) FROM TripMember tmM
                       JOIN TripMember meM ON meM.tripGroup = tmM.tripGroup
                       WHERE meM.member.id = :memberId
                         AND tmM.member.id = mate.member.id
                   )
             )) AS latestGroupName
        FROM TripMember me
        JOIN TripMember mate ON mate.tripGroup = me.tripGroup
        WHERE me.member.id = :memberId
          AND mate.member.id <> :memberId
          AND LOWER(mate.member.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
        GROUP BY mate.member.id, mate.member.name
        ORDER BY MAX(mate.tripGroup.startDate) DESC, mate.member.name ASC, mate.member.id ASC
        """
    )
    fun searchPastMatesByMemberId(
        @Param("memberId") memberId: Long,
        @Param("keyword") keyword: String,
        pageable: Pageable,
    ): Slice<PastMateProjection>
}
