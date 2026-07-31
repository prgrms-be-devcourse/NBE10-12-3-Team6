package csh.back.global.notification.controller

import csh.back.domain.member.dto.response.AuthFilterDto
import csh.back.global.annotation.ApiV1
import csh.back.global.dto.ResponseData
import csh.back.global.notification.dto.NotificationResponse
import csh.back.global.notification.service.NotificationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@ApiV1
@RequestMapping("/notifications")
@Tag(
    name = "알림",
    description = "사용자 알림 API"
)
class NotificationV1Controller(
    private val notificationService: NotificationService
) {

    @GetMapping
    @Operation(
        summary = "알림 목록 조회",
        description = "현재 로그인한 사용자의 알림을 최신순으로 조회합니다."
    )
    fun getNotifications(
        @AuthenticationPrincipal member: AuthFilterDto
    ): ResponseData<List<NotificationResponse>> {
        return ResponseData(
            statusCode = 200,
            data = notificationService.getNotifications(member.id)
        )
    }

    @GetMapping("/unread-count")
    @Operation(
        summary = "읽지 않은 알림 개수 조회",
        description = "현재 로그인한 사용자의 읽지 않은 알림 개수를 조회합니다."
    )
    fun getUnreadCount(
        @AuthenticationPrincipal member: AuthFilterDto
    ): ResponseData<Long> {
        return ResponseData(
            statusCode = 200,
            data = notificationService.getUnreadCount(member.id)
        )
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(
        summary = "알림 읽음 처리",
        description = "알림을 읽음 상태로 변경합니다."
    )
    fun markAsRead(
        @PathVariable notificationId: Long,
        @AuthenticationPrincipal member: AuthFilterDto
    ): ResponseData<NotificationResponse> {
        return ResponseData(
            statusCode = 200,
            data = notificationService.markAsRead(
                notificationId = notificationId,
                receiverId = member.id
            )
        )
    }
}