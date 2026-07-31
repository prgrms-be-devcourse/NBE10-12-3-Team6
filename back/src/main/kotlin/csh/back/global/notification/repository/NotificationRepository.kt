package csh.back.global.notification.repository

import csh.back.global.notification.entity.Notification
import csh.back.global.notification.entity.NotificationType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface NotificationRepository : JpaRepository<Notification, Long> {

    fun existsByReceiverIdAndTypeAndReferenceId(
        receiverId: Long,
        type: NotificationType,
        referenceId: Long
    ): Boolean

    fun findByReceiverIdAndTypeAndReferenceId(
        receiverId: Long,
        type: NotificationType,
        referenceId: Long
    ): Optional<Notification>

    fun findAllByReceiverIdOrderByCreatedAtDesc(
        receiverId: Long
    ): List<Notification>

    fun findByIdAndReceiverId(
        notificationId: Long,
        receiverId: Long
    ): Optional<Notification>

    fun countByReceiverIdAndIsReadFalse(
        receiverId: Long
    ): Long
}