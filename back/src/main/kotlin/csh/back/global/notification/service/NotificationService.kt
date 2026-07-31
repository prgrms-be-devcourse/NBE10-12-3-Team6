package csh.back.global.notification.service

import csh.back.domain.member.entity.Member
import csh.back.global.notification.dto.NotificationResponse
import csh.back.global.notification.entity.Notification
import csh.back.global.notification.entity.NotificationType
import csh.back.global.notification.repository.NotificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class NotificationService(
    private val notificationRepository: NotificationRepository
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createIfAbsent(
        receiver: Member,
        type: NotificationType,
        referenceId: Long,
        title: String,
        content: String,
        targetUrl: String
    ): Boolean {
        val receiverId = receiver.id
            ?: throw IllegalArgumentException(
                "알림을 받을 회원 ID가 존재하지 않습니다."
            )

        val alreadyExists =
            notificationRepository
                .existsByReceiverIdAndTypeAndReferenceId(
                    receiverId = receiverId,
                    type = type,
                    referenceId = referenceId
                )

        if (alreadyExists) {
            return false
        }

        notificationRepository.saveAndFlush(
            Notification.create(
                receiver = receiver,
                type = type,
                referenceId = referenceId,
                title = title,
                content = content,
                targetUrl = targetUrl
            )
        )

        return true
    }

    fun getNotifications(
        receiverId: Long
    ): List<NotificationResponse> {
        return notificationRepository
            .findAllByReceiverIdOrderByCreatedAtDesc(receiverId)
            .map(NotificationResponse::from)
    }

    fun getUnreadCount(
        receiverId: Long
    ): Long {
        return notificationRepository
            .countByReceiverIdAndIsReadFalse(receiverId)
    }

    @Transactional
    fun markAsRead(
        notificationId: Long,
        receiverId: Long
    ): NotificationResponse {
        val notification =
            notificationRepository
                .findByIdAndReceiverId(
                    notificationId = notificationId,
                    receiverId = receiverId
                )
                .orElseThrow {
                    IllegalArgumentException(
                        "알림이 존재하지 않거나 접근 권한이 없습니다."
                    )
                }

        notification.markAsRead()

        return NotificationResponse.from(notification)
    }
}