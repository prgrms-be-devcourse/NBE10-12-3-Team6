package csh.back.domain.trip.group.repository

import csh.back.domain.trip.group.entity.TripGroup
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface TripGroupRepository : JpaRepository<TripGroup, Long>, TripGroupRepositoryCustom {

    fun findAllByOwnerIdOrderByStartDateDesc(memberId: Long): List<TripGroup>

    @Query("SELECT tg FROM TripGroup tg JOIN TripMember tm ON tm.tripGroup = tg WHERE tm.member.id = :memberId ORDER BY tg.startDate DESC")
    fun findAllByMemberId(@Param("memberId") memberId: Long): List<TripGroup>

    fun existsByJoinCode(joinCode: String): Boolean

    fun findByJoinCode(joinCode: String): Optional<TripGroup>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select tg from TripGroup tg where tg.id = :tripId")
    fun findByIdWithLock(@Param("tripId") tripId: Long): Optional<TripGroup>
}
