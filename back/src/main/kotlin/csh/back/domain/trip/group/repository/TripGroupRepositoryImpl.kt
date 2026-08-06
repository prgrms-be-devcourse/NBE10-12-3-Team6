package csh.back.domain.trip.group.repository

import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import csh.back.domain.trip.group.dto.response.TripGroupResponse
import csh.back.domain.trip.group.entity.QTripGroup.tripGroup
import csh.back.domain.trip.member.entity.QTripMember
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.util.StringUtils.hasText

class TripGroupRepositoryImpl(
    private val jpaQueryFactory: JPAQueryFactory,
) : TripGroupRepositoryCustom {

    override fun findAllByMemberIdWithSearch(
        memberId: Long,
        keyword: String?,
        startDate: String?,
        pageable: Pageable,
    ): Slice<TripGroupResponse> {
        val me = QTripMember("me")
        val groupMember = QTripMember("groupMember")

        // N+1 방지를 위한 projection:
        //   TripGroup 엔티티를 통째로 로드하지 않고 필요한 컬럼만 SELECT → owner LAZY 프록시가 아예 안 생기고
        //   Member 테이블 조회 없이 owner.id(FK 컬럼)만 그대로 뽑아온다.
        //   Projections.constructor 인자 순서는 TripGroupResponse의 primary constructor와 정확히 일치해야 함.
        //
        // Slice 판정 트릭: pageSize+1개를 페치해서 실제 pageSize보다 많이 오면 hasNext=true.
        // count 쿼리를 아끼기 위한 방식(Page 대비 count 1번 절약).
        val fetched = jpaQueryFactory
            .select(
                Projections.constructor(
                    TripGroupResponse::class.java,
                    tripGroup.id,
                    tripGroup.name,
                    tripGroup.owner.id,
                    tripGroup.region,
                    tripGroup.joinCode,
                    tripGroup.nights,
                    tripGroup.startDate,
                    tripGroup.endDate,
                ),
            )
            .from(tripGroup)
            .join(me).on(me.tripGroup.eq(tripGroup))
            .leftJoin(groupMember).on(groupMember.tripGroup.eq(tripGroup))
            .where(
                me.member.id.eq(memberId),
                // @SQLRestriction("deleted_at IS NULL")은 QueryDSL에는 자동 적용되지 않으므로
                // 여기서 명시적으로 삭제된 방을 제외한다.
                tripGroup.deletedAt.isNull,
                keywordSearch(keyword, groupMember),
                dateSearch(startDate),
            )
            .orderBy(tripGroup.startDate.desc())
            .distinct()
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong() + 1)
            .fetch()

        val hasNext = fetched.size > pageable.pageSize
        val content = if (hasNext) fetched.dropLast(1) else fetched
        return SliceImpl(content, pageable, hasNext)
    }

    private fun keywordSearch(keyword: String?, groupMember: QTripMember): BooleanExpression? {
        if (!hasText(keyword)) return null
        return tripGroup.name.containsIgnoreCase(keyword)
            .or(tripGroup.region.containsIgnoreCase(keyword))
            .or(groupMember.member.name.containsIgnoreCase(keyword))
    }

    private fun dateSearch(startDate: String?): BooleanExpression? =
        if (hasText(startDate)) tripGroup.startDate.stringValue().containsIgnoreCase(startDate) else null
}
