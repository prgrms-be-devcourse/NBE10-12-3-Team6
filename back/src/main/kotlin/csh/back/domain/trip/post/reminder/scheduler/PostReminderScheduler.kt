package csh.back.domain.trip.post.reminder.scheduler

import csh.back.domain.trip.post.reminder.service.PostReminderService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PostReminderScheduler(
    private val postReminderService: PostReminderService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        cron = "0 0 9 * * *",
        zone = "Asia/Seoul"
    )
    fun sendOneYearAgoPostReminders() {
        log.info("여행 포스트 1년 리마인드 작업 시작")

        postReminderService.sendOneYearAgoPostReminders()

        log.info("여행 포스트 1년 리마인드 작업 종료")
    }
}