package csh.back.domain.trip.group.repository

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import csh.back.domain.trip.group.entity.QTripGroup.tripGroup
import csh.back.domain.trip.group.entity.TripGroup
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
    ): Slice<TripGroup> {
        val me = QTripMember("me")
        val groupMember = QTripMember("groupMember")

        // Slice 판정 트릭: pageSize+1개를 페치해서 실제 pageSize보다 많이 오면 hasNext=true.
        // count 쿼리를 아끼기 위한 방식(Page 대비 count 1번 절약).
        val fetched = jpaQueryFactory
            .selectFrom(tripGroup)
            .join(me).on(me.tripGroup.eq(tripGroup))
            .leftJoin(groupMember).on(groupMember.tripGroup.eq(tripGroup))
            .where(
                me.member.id.eq(memberId),
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
