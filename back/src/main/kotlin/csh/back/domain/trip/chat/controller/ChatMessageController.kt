package csh.back.domain.trip.chat.controller

import csh.back.domain.trip.chat.dto.request.ChatMessageSendRequest
import csh.back.domain.trip.chat.service.ChatService
import csh.back.domain.trip.chat.support.toAuthFilterDto
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageExceptionHandler
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.annotation.SendToUser
import org.springframework.stereotype.Controller
import java.security.Principal

@Controller
class ChatMessageController(
    private val chatService: ChatService,
) {

    @MessageMapping("/trips/{tripGroupId}/chat")
    fun sendMessage(
        @DestinationVariable tripGroupId: Long,
        request: ChatMessageSendRequest,
        principal: Principal,
    ) {
        chatService.sendMessage(tripGroupId, principal.toAuthFilterDto().id, request.content)
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    fun handleException(exception: RuntimeException): String = exception.message ?: "요청을 처리할 수 없습니다."
}
