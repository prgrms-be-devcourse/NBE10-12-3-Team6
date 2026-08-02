package csh.back.domain.trip.chat.repository

import com.querydsl.jpa.impl.JPAQueryFactory
import csh.back.domain.trip.chat.entity.QTripChatMessage.tripChatMessage
import csh.back.domain.trip.chat.entity.QTripChatReadStatus.tripChatReadStatus
import csh.back.domain.trip.chat.entity.TripChatMessage

class TripChatMessageRepositoryImpl(
    private val jpaQueryFactory: JPAQueryFactory,
) : TripChatMessageRepositoryCustom {

    override fun findLatestPage(tripGroupId: Long, cursor: Long?, size: Int): List<TripChatMessage> =
        jpaQueryFactory
            .selectFrom(tripChatMessage)
            .where(
                tripChatMessage.tripGroup.id.eq(tripGroupId),
                cursor?.let { tripChatMessage.id.lt(it) },
            )
            .orderBy(tripChatMessage.id.desc())
            .limit(size.toLong())
            .fetch()

    override fun findAfter(tripGroupId: Long, cursor: Long, size: Int): List<TripChatMessage> =
        jpaQueryFactory
            .selectFrom(tripChatMessage)
            .where(
                tripChatMessage.tripGroup.id.eq(tripGroupId),
                tripChatMessage.id.gt(cursor),
            )
            .orderBy(tripChatMessage.id.asc())
            .limit(size.toLong())
            .fetch()

    override fun countUnreadByMember(memberId: Long, tripGroupIds: List<Long>, cap: Int): Map<Long, Long> {
        if (tripGroupIds.isEmpty()) return emptyMap()

        val lastReadByTripGroupId = jpaQueryFactory
            .selectFrom(tripChatReadStatus)
            .where(
                tripChatReadStatus.tripGroup.id.`in`(tripGroupIds),
                tripChatReadStatus.member.id.eq(memberId),
            )
            .fetch()
            .associate { it.tripGroup.id!! to it.lastReadMessageId }

        return tripGroupIds.associateWith { tripGroupId ->
            val lastReadMessageId = lastReadByTripGroupId[tripGroupId] ?: 0L
            jpaQueryFactory
                .select(tripChatMessage.id)
                .from(tripChatMessage)
                .where(
                    tripChatMessage.tripGroup.id.eq(tripGroupId),
                    tripChatMessage.id.gt(lastReadMessageId),
                )
                .limit(cap.toLong())
                .fetch()
                .size
                .toLong()
        }
    }
}
