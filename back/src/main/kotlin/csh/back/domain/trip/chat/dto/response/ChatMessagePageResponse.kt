package csh.back.domain.trip.chat.dto.response

data class ChatMessagePageResponse(
    val messages: List<ChatMessageResponse>,
    val hasNext: Boolean,
)
