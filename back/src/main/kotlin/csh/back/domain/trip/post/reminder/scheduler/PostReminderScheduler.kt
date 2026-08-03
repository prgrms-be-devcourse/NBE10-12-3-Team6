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
        cron = "0 0 9 * * *", // 알람 시간은 매일아침 아홉시 설정, 리마인드를 확인했을경우 발동하지 않음
        //테스트를 위해 매 분으로 하고싶다면 cron값을 "0 * * * * *"으로 바꿔줄것
        zone = "Asia/Seoul"
    )
    fun sendOneYearAgoPostReminders() {
        log.info("여행 포스트 1년 리마인드 작업 시작")

        postReminderService.sendOneYearAgoPostReminders()

        log.info("여행 포스트 1년 리마인드 작업 종료")
    }
}