package csh.back.domain.trip.post.reminder.entity

import csh.back.domain.trip.post.entity.Post
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class PostReminderTest {

    @Test
    fun `전송 성공 상태와 메시지 ID를 기록한다`() {
        val reminder = PostReminder.create(mock(Post::class.java))

        reminder.markSent("firebase-message-id")

        assertThat(reminder.status).isEqualTo(ReminderStatus.SENT)
        assertThat(reminder.firebaseMessageId).isEqualTo("firebase-message-id")
        assertThat(reminder.sentAt).isNotNull()
        assertThat(reminder.failureReason).isNull()
    }

    @Test
    fun `실패 사유는 컬럼 길이에 맞춰 1000자로 제한한다`() {
        val reminder = PostReminder.create(mock(Post::class.java))

        reminder.markFailed("실패".repeat(600))

        assertThat(reminder.status).isEqualTo(ReminderStatus.FAILED)
        assertThat(reminder.failureReason).hasSize(1000)
    }
}
