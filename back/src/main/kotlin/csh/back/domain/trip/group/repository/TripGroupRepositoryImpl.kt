package csh.back.domain.trip.group.repository

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import csh.back.domain.trip.group.entity.QTripGroup.tripGroup
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.member.entity.QTripMember
import org.springframework.util.StringUtils.hasText

class TripGroupRepositoryImpl(
    private val jpaQueryFactory: JPAQueryFactory,
) : TripGroupRepositoryCustom {

    override fun findAllByMemberIdWithSearch(memberId: Long, keyword: String?, startDate: String?): List<TripGroup> {
        val me = QTripMember("me")
        val groupMember = QTripMember("groupMember")

        return jpaQueryFactory
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
            .fetch()
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
