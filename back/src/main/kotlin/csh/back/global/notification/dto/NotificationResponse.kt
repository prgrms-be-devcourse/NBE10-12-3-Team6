package csh.back.global.notification.dto

import csh.back.global.notification.entity.Notification
import csh.back.global.notification.entity.NotificationType
import java.time.LocalDateTime

data class NotificationResponse(
    val notificationId: Long,
    val type: NotificationType,
    val referenceId: Long,
    val title: String,
    val content: String,
    val targetUrl: String,
    val isRead: Boolean,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(notification: Notification): NotificationResponse {
            return NotificationResponse(
                notificationId = notification.id!!,
                type = notification.type,
                referenceId = notification.referenceId,
                title = notification.title,
                content = notification.content,
                targetUrl = notification.targetUrl,
                isRead = notification.isRead,
                createdAt = notification.createdAt!!
            )
        }
    }
}