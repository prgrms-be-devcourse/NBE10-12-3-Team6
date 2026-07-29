package csh.back.domain.trip.member.repository

import csh.back.domain.trip.member.entity.TripMember
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface TripMemberRepository : JpaRepository<TripMember, Long> {

    fun existsByTripGroupIdAndMemberId(tripGroupId: Long, memberId: Long): Boolean

    fun existsByTripGroupIdAndMemberIdAndIsAdminTrue(tripGroupId: Long, memberId: Long): Boolean

    fun findByMemberIdAndTripGroupId(memberId: Long, tripGroupId: Long): Optional<TripMember>

    fun findByMemberId(memberId: Long): Optional<TripMember>

    fun findByTripGroupId(tripGroupId: Long): List<TripMember>
}
