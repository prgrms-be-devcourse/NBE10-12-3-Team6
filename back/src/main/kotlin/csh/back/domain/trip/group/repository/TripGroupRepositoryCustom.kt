package csh.back.domain.trip.group.repository

import csh.back.domain.trip.group.dto.response.TripGroupResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice

interface TripGroupRepositoryCustom {
    // 반환 타입이 엔티티(TripGroup)가 아닌 DTO(TripGroupResponse)인 이유:
    // owner LAZY 프록시 초기화로 인한 N+1을 원천 차단. QueryDSL Projections.constructor로
    // 필요한 컬럼만 SELECT하여 Member 엔티티를 아예 로드하지 않는다.
    fun findAllByMemberIdWithSearch(
        memberId: Long,
        keyword: String?,
        startDate: String?,
        pageable: Pageable,
    ): Slice<TripGroupResponse>
}
