package csh.back.domain.trip.chat.entity

import csh.back.domain.member.entity.Member
import csh.back.domain.trip.chat.enums.MessageType
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(
    name = "trip_chat_messages",
    indexes = [
        Index(name = "idx_trip_chat_messages_trip_group_id_id", columnList = "trip_group_id, id DESC"),
    ],
)
class TripChatMessage(
    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "trip_group_id", nullable = false)
    val tripGroup: TripGroup,
    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "sender_id", nullable = true)
    val sender: Member?,
    @field:Enumerated(STRING)
    @field:Column(name = "message_type", nullable = false, length = 20)
    val messageType: MessageType,
    @field:Column(nullable = false, length = 1000)
    val content: String,
) : BaseEntity()
