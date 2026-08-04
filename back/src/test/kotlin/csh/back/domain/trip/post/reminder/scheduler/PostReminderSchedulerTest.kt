package csh.back.domain.trip.post.reminder.scheduler

import csh.back.domain.trip.post.reminder.service.PostReminderService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class PostReminderSchedulerTest {

    @Test
    fun `스케줄 실행 시 리마인드 서비스를 호출한다`() {
        val service = mock(PostReminderService::class.java)
        val scheduler = PostReminderScheduler(service)

        scheduler.sendOneYearAgoPostReminders()

        verify(service).sendOneYearAgoPostReminders()
    }
}
