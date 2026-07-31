package csh.back.global.notification.entity

import csh.back.domain.member.entity.Member
import csh.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "notifications",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_notification_receiver_type_reference",
            columnNames = [
                "receiver_id",
                "type",
                "reference_id"
            ]
        )
    ]
)
class Notification protected constructor() : BaseEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "receiver_id", nullable = false)
    lateinit var receiver: Member
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 50)
    lateinit var type: NotificationType
        protected set

    @field:Column(name = "reference_id", nullable = false)
    var referenceId: Long = 0L
        protected set

    @field:Column(nullable = false, length = 100)
    lateinit var title: String
        protected set

    @field:Column(nullable = false, length = 500)
    lateinit var content: String
        protected set

    @field:Column(name = "target_url", nullable = false, length = 300)
    lateinit var targetUrl: String
        protected set

    @field:Column(name = "is_read", nullable = false)
    var isRead: Boolean = false
        protected set

    private constructor(
        receiver: Member,
        type: NotificationType,
        referenceId: Long,
        title: String,
        content: String,
        targetUrl: String
    ) : this() {
        this.receiver = receiver
        this.type = type
        this.referenceId = referenceId
        this.title = title
        this.content = content
        this.targetUrl = targetUrl
    }

    fun markAsRead() {
        isRead = true
    }

    companion object {
        fun create(
            receiver: Member,
            type: NotificationType,
            referenceId: Long,
            title: String,
            content: String,
            targetUrl: String
        ): Notification {
            return Notification(
                receiver = receiver,
                type = type,
                referenceId = referenceId,
                title = title,
                content = content,
                targetUrl = targetUrl
            )
        }
    }
}