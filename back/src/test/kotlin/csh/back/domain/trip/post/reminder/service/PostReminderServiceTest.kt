package csh.back.domain.trip.post.reminder.service

import csh.back.domain.trip.post.repository.PostRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class PostReminderServiceTest {

    @Mock lateinit var postRepository: PostRepository
    @Mock lateinit var postReminderSender: PostReminderSender
    @InjectMocks lateinit var postReminderService: PostReminderService

    @Test
    fun `정확히 1년 전 하루 동안 생성된 게시글을 조회해 발송한다`() {
        val today = LocalDate.of(2026, 8, 4)
        val targetDate = LocalDate.of(2025, 8, 4)
        doReturn(listOf(10L, 20L))
            .`when`(postRepository)
            .findIdsByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                targetDate.atStartOfDay(),
                targetDate.plusDays(1).atStartOfDay()
            )

        postReminderService.sendOneWeekAgoPostReminders(today)

        verify(postReminderSender).send(10L)
        verify(postReminderSender).send(20L)
    }

    @Test
    fun `한 게시글 발송이 실패해도 다음 게시글을 계속 처리한다`() {
        val today = LocalDate.of(2026, 8, 4)
        val targetDate = LocalDate.of(2025, 8, 4)
        doReturn(listOf(10L, 20L))
            .`when`(postRepository)
            .findIdsByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                targetDate.atStartOfDay(),
                targetDate.plusDays(1).atStartOfDay()
            )
        doThrow(IllegalStateException("발송 실패"))
            .`when`(postReminderSender)
            .send(10L)

        postReminderService.sendOneWeekAgoPostReminders(today)

        verify(postReminderSender).send(20L)
    }
}
