package csh.back.domain.trip.group.repository

import csh.back.domain.trip.group.entity.TripGroup
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice

interface TripGroupRepositoryCustom {
    fun findAllByMemberIdWithSearch(
        memberId: Long,
        keyword: String?,
        startDate: String?,
        pageable: Pageable,
    ): Slice<TripGroup>
}
