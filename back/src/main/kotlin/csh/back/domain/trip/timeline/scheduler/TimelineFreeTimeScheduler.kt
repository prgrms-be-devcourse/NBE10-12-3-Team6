package csh.back.domain.trip.timeline.scheduler

import csh.back.domain.trip.timeline.repository.TimelineRepository
import csh.back.domain.trip.timeline.service.TimelineFreeTimeService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

@Component
class TimelineFreeTimeScheduler(
    private val timelineRepository: TimelineRepository,
    private val timelineFreeTimeService: TimelineFreeTimeService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    fun createDailyFreeTimes() {
        val today = LocalDate.now(SEOUL_ZONE_ID)

        timelineRepository.findActiveTripGroupIds(today).forEach { tripGroupId ->
            try {
                timelineFreeTimeService.createForDate(tripGroupId, today)
            } catch (exception: Exception) {
                log.error("자유시간 생성 실패: tripGroupId={}", tripGroupId, exception)
            }
        }
    }

    companion object {
        private val SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul")
    }
}
