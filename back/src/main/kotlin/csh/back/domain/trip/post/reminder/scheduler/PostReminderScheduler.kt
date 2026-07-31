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
    fun createOneYearPostReminders() {
        log.info("1년 POST 리마인드 알림 생성 작업을 시작합니다.")

        val createdCount =
            postReminderService.createOneYearReminders()

        log.info(
            "1년 POST 리마인드 알림 생성 작업을 완료했습니다. createdCount={}",
            createdCount
        )
    }
}