package csh.back.domain.trip.chat.controller

import csh.back.domain.member.dto.response.AuthFilterDto
import csh.back.domain.trip.chat.dto.request.ChatReadRequest
import csh.back.domain.trip.chat.dto.response.ChatMessagePageResponse
import csh.back.domain.trip.chat.dto.response.ChatReadStatusPageResponse
import csh.back.domain.trip.chat.service.ChatService
import csh.back.global.annotation.ApiV1
import csh.back.global.dto.ResponseData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@ApiV1
@RequestMapping("/trips")
@RestController
@Tag(name = "여행 채팅", description = "여행 모임 채팅 관련 API")
class ChatV1Controller(
    private val chatService: ChatService,
) {

    @Operation(summary = "채팅 메시지 히스토리 조회 (커서 기반 페이징)")
    @GetMapping("/{tripGroupId}/chat/messages")
    fun findMessages(
        @PathVariable tripGroupId: Long,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "30") size: Int,
        @AuthenticationPrincipal member: AuthFilterDto,
    ): ResponseData<ChatMessagePageResponse> = ResponseData(
        200,
        chatService.getHistory(tripGroupId, member.id, cursor, size),
    )

    @Operation(summary = "재접속 시 유실 메시지 복구 조회")
    @GetMapping("/{tripGroupId}/chat/messages/after")
    fun findMessagesAfter(
        @PathVariable tripGroupId: Long,
        @RequestParam cursor: Long,
        @RequestParam(defaultValue = "100") size: Int,
        @AuthenticationPrincipal member: AuthFilterDto,
    ): ResponseData<ChatMessagePageResponse> = ResponseData(
        200,
        chatService.getMessagesAfter(tripGroupId, member.id, cursor, size),
    )

    @Operation(summary = "채팅 읽음 처리")
    @PutMapping("/{tripGroupId}/chat/read")
    fun markAsRead(
        @PathVariable tripGroupId: Long,
        @RequestBody request: ChatReadRequest,
        @AuthenticationPrincipal member: AuthFilterDto,
    ) = chatService.markAsRead(tripGroupId, member.id, request.lastReadMessageId)

    @Operation(summary = "여행별 안 읽은 채팅 메시지 수 조회")
    @GetMapping("/chat/unread-counts")
    fun findUnreadCounts(
        @AuthenticationPrincipal member: AuthFilterDto,
    ): ResponseData<Map<Long, Long>> = ResponseData(200, chatService.getUnreadCounts(member.id))

    @Operation(summary = "멤버별 채팅 읽음 상태 및 전체 인원 조회 (메시지별 안읽은 사람 수 표시용)")
    @GetMapping("/{tripGroupId}/chat/read-statuses")
    fun findReadStatuses(
        @PathVariable tripGroupId: Long,
        @AuthenticationPrincipal member: AuthFilterDto,
    ): ResponseData<ChatReadStatusPageResponse> = ResponseData(
        200,
        chatService.getReadStatuses(tripGroupId, member.id),
    )
}
