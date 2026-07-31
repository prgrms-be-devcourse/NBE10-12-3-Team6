package csh.back.domain.trip.timeline.service

import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.group.settings.entity.TripGroupSettings
import csh.back.domain.trip.group.settings.repository.TripGroupSettingsRepository
import csh.back.domain.trip.timeline.entity.Timeline
import csh.back.domain.trip.timeline.repository.TimelineRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Service
class TimelineFreeTimeService(
    private val timelineRepository: TimelineRepository,
    private val tripGroupRepository: TripGroupRepository,
    private val tripGroupSettingsRepository: TripGroupSettingsRepository,
) {

    @Transactional
    fun createForToday(tripGroupId: Long): List<Timeline> =
        createForDate(
            tripGroupId = tripGroupId,
            travelDate = LocalDate.now(SEOUL_ZONE_ID),
        )

    @Transactional
    fun createForDate(
        tripGroupId: Long,
        travelDate: LocalDate,
    ): List<Timeline> {
        val tripGroup = tripGroupRepository.findByIdWithLock(tripGroupId)
            .orElseThrow {
                IllegalArgumentException("여행 모임을 찾을 수 없습니다.")
            }

        if (travelDate.isBefore(tripGroup.startDate) || travelDate.isAfter(tripGroup.endDate)) {
            return emptyList()
        }

        val dayNumber = ChronoUnit.DAYS.between(tripGroup.startDate, travelDate) + MINIMUM_DAY
        val tripGroupSettings = findTripGroupSettings(tripGroup)
        if (tripGroupSettings.lastFreeTimeGeneratedDay >= dayNumber) {
            return emptyList()
        }

        if (timelineRepository.existsByTripGroupIdAndDayNumberAndIsFreeTimeTrue(tripGroupId, dayNumber)) {
            tripGroupSettings.markFreeTimeGenerated(dayNumber)
            return emptyList()
        }

        val freeTimeMinutes = tripGroupSettings.freeTimeMinutesFor(dayNumber)
        TripGroupSettings.validateFreeTimeMinutes(freeTimeMinutes)

        val freeTimeWindowStart = travelDate.atStartOfDay()
        val freeTimeWindowEnd = travelDate.atTime(FREE_TIME_END)
        val plannedTimelines = timelineRepository
            .findAllByTripGroupIdAndDayNumberAndIsFreeTimeFalseOrderByStartTimeAsc(
                tripGroupId,
                dayNumber,
            )
        val freeTimeGaps = findFreeTimeGaps(
            dayNumber = dayNumber,
            plannedTimelines = plannedTimelines,
            windowStart = freeTimeWindowStart,
            windowEnd = freeTimeWindowEnd,
        )
        val freeTimelines = freeTimeGaps.flatMap { (gapStart, gapEnd) ->
            splitFreeTimeGap(
                tripGroup = tripGroup,
                dayNumber = dayNumber,
                gapStart = gapStart,
                gapEnd = gapEnd,
                freeTimeMinutes = freeTimeMinutes,
            )
        }

        val savedFreeTimelines = timelineRepository.saveAll(freeTimelines)
        tripGroupSettings.markFreeTimeGenerated(dayNumber)
        return savedFreeTimelines
    }

    private fun findFreeTimeGaps(
        dayNumber: Long,
        plannedTimelines: List<Timeline>,
        windowStart: LocalDateTime,
        windowEnd: LocalDateTime,
    ): List<Pair<LocalDateTime, LocalDateTime>> {
        if (plannedTimelines.isEmpty()) {
            return listOf(windowStart to windowEnd)
        }

        val gaps = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        val timelinesToScan: List<Timeline>
        var cursor: LocalDateTime

        if (dayNumber == FIRST_DAY) {
            cursor = minOf(
                maxOf(plannedTimelines.first().endTime, windowStart),
                windowEnd,
            )
            timelinesToScan = plannedTimelines.drop(1)
        } else {
            cursor = windowStart
            timelinesToScan = plannedTimelines
        }

        for (timeline in timelinesToScan) {
            if (!cursor.isBefore(windowEnd)) {
                break
            }

            val plannedStart = minOf(
                maxOf(timeline.startTime, windowStart),
                windowEnd,
            )
            val plannedEnd = minOf(
                maxOf(timeline.endTime, windowStart),
                windowEnd,
            )

            if (cursor.isBefore(plannedStart)) {
                gaps += cursor to plannedStart
            }
            if (cursor.isBefore(plannedEnd)) {
                cursor = plannedEnd
            }
        }

        if (cursor.isBefore(windowEnd)) {
            gaps += cursor to windowEnd
        }

        return gaps
    }

    private fun splitFreeTimeGap(
        tripGroup: TripGroup,
        dayNumber: Long,
        gapStart: LocalDateTime,
        gapEnd: LocalDateTime,
        freeTimeMinutes: Int,
    ): List<Timeline> {
        val freeTimelines = mutableListOf<Timeline>()
        var freeTimeStart = gapStart

        while (freeTimeStart.isBefore(gapEnd)) {
            val freeTimeEnd = minOf(
                freeTimeStart.plusMinutes(freeTimeMinutes.toLong()),
                gapEnd,
            )
            freeTimelines += Timeline.createFreeTime(
                tripGroup = tripGroup,
                dayNumber = dayNumber,
                startTime = freeTimeStart,
                endTime = freeTimeEnd,
            )
            freeTimeStart = freeTimeEnd
        }

        return freeTimelines
    }

    private fun findTripGroupSettings(tripGroup: TripGroup): TripGroupSettings =
        tripGroupSettingsRepository.findByTripGroupId(requireNotNull(tripGroup.id))
            .orElseGet {
                tripGroupSettingsRepository.save(
                    TripGroupSettings(tripGroup = tripGroup),
                )
            }

    companion object {
        private const val MINIMUM_DAY = 1L
        private const val FIRST_DAY = 1L
        private val FREE_TIME_END = LocalTime.of(23, 59)
        private val SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul")
    }
}
