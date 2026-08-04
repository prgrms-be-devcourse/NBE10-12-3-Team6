package csh.back.domain.trip.group.entity

import csh.back.domain.member.entity.Member
import csh.back.domain.trip.group.dto.request.TripGroupModifyRequest
import csh.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnDefault
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate
import java.time.LocalDateTime

// Soft delete:
//   @SQLDelete: repository.delete(group) 호출 시 실제 DELETE 대신 UPDATE로 deleted_at을 채움.
//   @SQLRestriction: JPA 조회에 자동으로 WHERE deleted_at IS NULL 을 붙임.
//     주의: QueryDSL 쿼리에는 자동 적용되지 않으므로 TripGroupRepositoryImpl에서 명시적으로 조건을 넣어야 함.
@Entity
@Table(name = "trip_groups")
@SQLDelete(sql = "UPDATE trip_groups SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
class TripGroup(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val owner: Member,

    var name: String,
    var region: String,
    var nights: Int,

    @Column(unique = true)
    val joinCode: String,

    var startDate: LocalDate,
    var endDate: LocalDate,
) : BaseEntity() {

    @ColumnDefault("false")
    var isVote: Boolean = false

    // @SQLDelete가 UPDATE로 채워주는 컬럼. null이면 살아있는 방, 값이 있으면 삭제된 방.
    // 애플리케이션 코드에서 이 필드를 직접 세팅하지 말고 repository.delete()를 통해서만 세팅해야
    // @SQLDelete의 자동 처리와 일관성이 유지됨.
    var deletedAt: LocalDateTime? = null

    fun modify(request: TripGroupModifyRequest) {
        if (!request.name.isNullOrBlank()) this.name = request.name
        if (!request.region.isNullOrBlank()) this.region = request.region
        if (!request.startDate.isNullOrBlank()) {
            this.startDate = LocalDate.parse(request.startDate)
            this.endDate = this.startDate.plusDays(this.nights.toLong())
        }
        if (request.nights != null) {
            this.nights = request.nights
            this.endDate = this.startDate.plusDays(request.nights.toLong())
        }
    }
}
