package csh.back.domain.trip.chat.dto.response

import csh.back.domain.trip.chat.entity.TripChatReadStatus

data class ChatReadStatusResponse(
    val memberId: Long,
    val lastReadMessageId: Long,
) {
    companion object {
        @JvmStatic
        fun from(readStatus: TripChatReadStatus): ChatReadStatusResponse = ChatReadStatusResponse(
            memberId = requireNotNull(readStatus.member.id),
            lastReadMessageId = readStatus.lastReadMessageId,
        )
    }
}

data class ChatReadStatusPageResponse(
    val totalMemberCount: Int,
    val statuses: List<ChatReadStatusResponse>,
)
