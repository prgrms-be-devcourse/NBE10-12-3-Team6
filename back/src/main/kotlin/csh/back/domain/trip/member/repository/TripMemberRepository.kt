package csh.back.domain.trip.member.repository

import csh.back.domain.trip.member.dto.response.PastMateResponse
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

    // 내가 참여했던 여행방의 다른 멤버들을, 함께한 여행 횟수/최근 시작일과 함께 최근순으로 반환.
    // Slice: hasNext만 알면 되는 무한 스크롤용 (Page와 달리 count 쿼리 안 함)
    @Query(
        """
        SELECT new csh.back.domain.trip.member.dto.response.PastMateResponse(
            mate.member.id,
            mate.member.name,
            mate.member.email,
            COUNT(DISTINCT mate.tripGroup.id),
            MAX(mate.tripGroup.startDate)
        )
        FROM TripMember me
        JOIN TripMember mate ON mate.tripGroup = me.tripGroup
        WHERE me.member.id = :memberId
          AND mate.member.id <> :memberId
        GROUP BY mate.member.id, mate.member.name, mate.member.email
        ORDER BY MAX(mate.tripGroup.startDate) DESC, mate.member.name ASC, mate.member.id ASC
        """
    )
    fun findPastMatesByMemberId(
        @Param("memberId") memberId: Long,
        pageable: Pageable,
    ): Slice<PastMateResponse>

    // 이름 키워드로 지난 메이트 필터링 (대소문자 무시)
    @Query(
        """
        SELECT new csh.back.domain.trip.member.dto.response.PastMateResponse(
            mate.member.id,
            mate.member.name,
            mate.member.email,
            COUNT(DISTINCT mate.tripGroup.id),
            MAX(mate.tripGroup.startDate)
        )
        FROM TripMember me
        JOIN TripMember mate ON mate.tripGroup = me.tripGroup
        WHERE me.member.id = :memberId
          AND mate.member.id <> :memberId
          AND LOWER(mate.member.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
        GROUP BY mate.member.id, mate.member.name, mate.member.email
        ORDER BY MAX(mate.tripGroup.startDate) DESC, mate.member.name ASC, mate.member.id ASC
        """
    )
    fun searchPastMatesByMemberId(
        @Param("memberId") memberId: Long,
        @Param("keyword") keyword: String,
        pageable: Pageable,
    ): Slice<PastMateResponse>
}
