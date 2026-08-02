package csh.back.domain.trip.post.reminder.entity

import csh.back.domain.trip.post.entity.Post
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
import java.time.LocalDateTime

@Entity
@Table(
    name = "post_reminders",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_post_reminders_post_id",
            columnNames = ["post_id"]
        )
    ]
)
class PostReminder protected constructor() : BaseEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "post_id", nullable = false)
    lateinit var post: Post
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20)
    var status: ReminderStatus = ReminderStatus.PENDING
        protected set

    @field:Column(name = "sent_at")
    var sentAt: LocalDateTime? = null
        protected set

    @field:Column(name = "firebase_message_id", length = 300)
    var firebaseMessageId: String? = null
        protected set

    @field:Column(name = "failure_reason", length = 1000)
    var failureReason: String? = null
        protected set

    private constructor(post: Post) : this() {
        this.post = post
    }

    fun markSent(messageId: String) {
        status = ReminderStatus.SENT
        firebaseMessageId = messageId
        failureReason = null
        sentAt = LocalDateTime.now()
    }

    fun markFailed(reason: String) {
        status = ReminderStatus.FAILED
        failureReason = reason.take(1000)
    }

    companion object {
        fun create(post: Post): PostReminder {
            return PostReminder(post)
        }
    }
}