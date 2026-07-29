package csh.back.domain.trip.group.repository

import csh.back.domain.trip.group.entity.TripGroup

interface TripGroupRepositoryCustom {
    fun findAllByMemberIdWithSearch(memberId: Long, keyword: String?, startDate: String?): List<TripGroup>
}
