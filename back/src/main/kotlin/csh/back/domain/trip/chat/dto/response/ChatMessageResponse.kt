package csh.back.domain.trip.chat.dto.response

import csh.back.domain.trip.chat.entity.TripChatMessage
import csh.back.domain.trip.chat.enums.MessageType
import java.time.LocalDateTime

data class ChatMessageResponse(
    val id: Long,
    val tripGroupId: Long,
    val senderId: Long?,
    val senderName: String?,
    val messageType: MessageType,
    val content: String,
    val createdAt: LocalDateTime,
) {
    companion object {
        @JvmStatic
        fun from(message: TripChatMessage, senderName: String?): ChatMessageResponse = ChatMessageResponse(
            id = requireNotNull(message.id),
            tripGroupId = requireNotNull(message.tripGroup.id),
            senderId = message.sender?.id,
            senderName = senderName,
            messageType = message.messageType,
            content = message.content,
            createdAt = requireNotNull(message.createdAt),
        )
    }
}
