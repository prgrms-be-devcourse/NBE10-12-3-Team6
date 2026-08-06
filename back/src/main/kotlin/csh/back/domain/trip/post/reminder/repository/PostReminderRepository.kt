package csh.back.domain.trip.post.reminder.repository

import csh.back.domain.trip.post.reminder.entity.PostReminder
import org.springframework.data.jpa.repository.JpaRepository

interface PostReminderRepository :
    JpaRepository<PostReminder, Long> {

    fun existsByPostId(postId: Long): Boolean

    fun deleteAllByPostId(postId: Long): Long
}
