package csh.back.domain.trip.chat.entity

import csh.back.domain.member.entity.Member
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "trip_chat_read_status",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_trip_chat_read_trip_group_member", columnNames = ["trip_group_id", "member_id"]),
    ],
)
class TripChatReadStatus(
    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "trip_group_id", nullable = false)
    val tripGroup: TripGroup,
    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "member_id", nullable = false)
    val member: Member,
) : BaseEntity() {

    @field:Column(name = "last_read_message_id", nullable = false)
    var lastReadMessageId: Long = 0
        protected set

    // 재접속 등으로 오래된 read 이벤트가 늦게 도착해도 last_read_message_id가 역행하지 않도록 방지
    fun updateLastReadMessageId(messageId: Long) {
        if (messageId > lastReadMessageId) {
            lastReadMessageId = messageId
        }
    }
}
