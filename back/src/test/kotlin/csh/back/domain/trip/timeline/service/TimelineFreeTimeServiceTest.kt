package csh.back.domain.trip.timeline.service

import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.group.settings.entity.TripGroupSettings
import csh.back.domain.trip.group.settings.repository.TripGroupSettingsRepository
import csh.back.domain.trip.post.service.S3UploadService
import csh.back.domain.trip.timeline.dto.response.TimelineWithVoteIdResponse
import csh.back.domain.trip.timeline.entity.Timeline
import csh.back.domain.trip.timeline.repository.TimelineRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@ActiveProfiles("test")
@SpringBootTest(
    properties = [
        "cloud.aws.credentials.access-key=test",
        "cloud.aws.credentials.secret-key=test",
        "cloud.aws.region.static=ap-northeast-2",
        "cloud.aws.s3.endpoint=http://localhost",
    ],
)
@Transactional
class TimelineFreeTimeServiceTest {

    @Autowired
    private lateinit var timelineFreeTimeService: TimelineFreeTimeService

    @Autowired
    private lateinit var timelineRepository: TimelineRepository

    @Autowired
    private lateinit var tripGroupRepository: TripGroupRepository

    @Autowired
    private lateinit var tripGroupSettingsRepository: TripGroupSettingsRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @MockitoBean
    private lateinit var s3UploadService: S3UploadService

    @Test
    @DisplayName("1일차는 계획 전 시간을 제외하고 계획 사이와 마지막 계획 이후를 자유시간으로 저장한다")
    fun createsFreeTimesBetweenAndAfterFirstDayPlans() {
        val travelDate = LocalDate.of(2026, 7, 31)
        val tripGroup = createTripGroup(travelDate)
        tripGroupSettingsRepository.save(
            TripGroupSettings(
                tripGroup = tripGroup,
                freeTimeMinutes = 60,
            ),
        )
        timelineRepository.saveAll(
            listOf(
                Timeline.create(
                    tripGroup = tripGroup,
                    dayNumber = 1L,
                    startTime = travelDate.atTime(10, 0),
                    endTime = travelDate.atTime(11, 0),
                ),
                Timeline.create(
                    tripGroup = tripGroup,
                    dayNumber = 1L,
                    startTime = travelDate.atTime(13, 30),
                    endTime = travelDate.atTime(15, 0),
                ),
            ),
        )

        val created = timelineFreeTimeService.createForDate(
            tripGroupId = requireNotNull(tripGroup.id),
            travelDate = travelDate,
        )

        assertThat(created).hasSize(12)
        assertThat(created.take(3).map { timeline -> timeline.startTime.toLocalTime() })
            .containsExactly(
                LocalTime.of(11, 0),
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
            )
        assertThat(created.take(3).map { timeline -> timeline.endTime.toLocalTime() })
            .containsExactly(
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                LocalTime.of(13, 30),
            )
        assertThat(created).allMatch { timeline -> !timeline.startTime.isBefore(travelDate.atTime(11, 0)) }
        assertThat(created.last().startTime).isEqualTo(travelDate.atTime(23, 0))
        assertThat(created.last().endTime).isEqualTo(travelDate.atTime(23, 59))
        assertThat(created).allMatch { timeline -> timeline.isFreeTime }

        val response = TimelineWithVoteIdResponse.of(created.first(), voteId = 1L)
        assertThat(response.confirmedPlaceName).isEqualTo("자유시간")
        assertThat(response.voteId).isNull()
        assertThat(response.isFreeTime).isTrue()

        val duplicated = timelineFreeTimeService.createForDate(
            tripGroupId = requireNotNull(tripGroup.id),
            travelDate = travelDate,
        )
        assertThat(duplicated).isEmpty()
    }

    @Test
    @DisplayName("1일차에 계획이 없으면 자정부터 오후 11시 59분까지 자유시간을 저장한다")
    fun createFreeTimesFromStartOfDayWhenTimelineDoesNotExist() {
        val travelDate = LocalDate.of(2026, 8, 1)
        val tripGroup = createTripGroup(travelDate)

        val created = timelineFreeTimeService.createForDate(
            tripGroupId = requireNotNull(tripGroup.id),
            travelDate = travelDate,
        )

        assertThat(created).hasSize(24)
        assertThat(created.first().startTime).isEqualTo(travelDate.atStartOfDay())
        assertThat(created.last().endTime).isEqualTo(travelDate.atTime(23, 59))
        assertThat(Duration.between(created.last().startTime, created.last().endTime).toMinutes())
            .isEqualTo(59)

        val settings = tripGroupSettingsRepository.findByTripGroupId(requireNotNull(tripGroup.id)).orElseThrow()
        assertThat(settings.freeTimeMinutes).isEqualTo(60)
        assertThat(settings.lastFreeTimeGeneratedDay).isEqualTo(1L)
    }

    @Test
    @DisplayName("여행 기간에 해당하지 않는 날짜에는 자유시간을 저장하지 않는다")
    fun doesNotCreateFreeTimesOutsideTravelPeriod() {
        val travelDate = LocalDate.of(2026, 8, 2)
        val tripGroup = createTripGroup(travelDate)

        val created = timelineFreeTimeService.createForDate(
            tripGroupId = requireNotNull(tripGroup.id),
            travelDate = travelDate.minusDays(1),
        )

        assertThat(created).isEmpty()
        assertThat(
            timelineRepository.findByTripGroupIdAndDayNumberOrderByStartTimeAsc(
                requireNotNull(tripGroup.id),
                1L,
            ),
        ).isEmpty()
    }

    @Test
    @DisplayName("설정 시간보다 짧게 남은 자투리 구간도 자유시간으로 저장한다")
    fun createsLastFreeTimeEvenWhenShorterThanSetting() {
        val travelDate = LocalDate.of(2026, 8, 3)
        val tripGroup = createTripGroup(travelDate)
        val settings = tripGroupSettingsRepository.save(
            TripGroupSettings(
                tripGroup = tripGroup,
                freeTimeMinutes = 30,
            ),
        )
        timelineRepository.save(
            Timeline.create(
                tripGroup = tripGroup,
                dayNumber = 1L,
                startTime = travelDate.atTime(22, 0),
                endTime = travelDate.atTime(23, 45),
            ),
        )

        val firstResult = timelineFreeTimeService.createForDate(
            tripGroupId = requireNotNull(tripGroup.id),
            travelDate = travelDate,
        )
        val secondResult = timelineFreeTimeService.createForDate(
            tripGroupId = requireNotNull(tripGroup.id),
            travelDate = travelDate,
        )

        assertThat(firstResult).hasSize(1)
        assertThat(firstResult.single().startTime).isEqualTo(travelDate.atTime(23, 45))
        assertThat(firstResult.single().endTime).isEqualTo(travelDate.atTime(23, 59))
        assertThat(secondResult).isEmpty()
        assertThat(settings.lastFreeTimeGeneratedDay).isEqualTo(1L)
        assertThat(
            timelineRepository.existsByTripGroupIdAndDayNumberAndIsFreeTimeTrue(
                requireNotNull(tripGroup.id),
                1L,
            ),
        ).isTrue()
    }

    @Test
    @DisplayName("자유시간 설정이 30분 단위가 아니면 생성하지 않는다")
    fun rejectsFreeTimeSettingThatIsNotThirtyMinuteUnit() {
        val travelDate = LocalDate.of(2026, 8, 4)
        val tripGroup = createTripGroup(travelDate)
        tripGroupSettingsRepository.save(
            TripGroupSettings(
                tripGroup = tripGroup,
                freeTimeMinutes = 45,
            ),
        )

        assertThatThrownBy {
            timelineFreeTimeService.createForDate(
                tripGroupId = requireNotNull(tripGroup.id),
                travelDate = travelDate,
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("자유시간은 30분 단위로 30분 이상 3시간 이하이어야 합니다.")
    }

    @Test
    @DisplayName("일차별 자유시간 설정값으로 해당 일차의 자유시간을 나누어 저장한다")
    fun createsFreeTimesWithDaySpecificSetting() {
        val startDate = LocalDate.of(2026, 8, 5)
        val tripGroup = createTripGroup(
            startDate = startDate,
            nights = 1,
        )
        tripGroupSettingsRepository.save(
            TripGroupSettings(
                tripGroup = tripGroup,
                freeTimeMinutesByDay = mutableMapOf(2 to 120),
            ),
        )

        val created = timelineFreeTimeService.createForDate(
            tripGroupId = requireNotNull(tripGroup.id),
            travelDate = startDate.plusDays(1),
        )

        assertThat(created).hasSize(12)
        assertThat(created.dropLast(1)).allSatisfy { timeline ->
            assertThat(Duration.between(timeline.startTime, timeline.endTime).toMinutes()).isEqualTo(120)
            assertThat(timeline.dayNumber).isEqualTo(2L)
        }
        assertThat(created.first().startTime).isEqualTo(startDate.plusDays(1).atStartOfDay())
        assertThat(created.last().startTime).isEqualTo(startDate.plusDays(1).atTime(22, 0))
        assertThat(created.last().endTime).isEqualTo(startDate.plusDays(1).atTime(23, 59))
        assertThat(Duration.between(created.last().startTime, created.last().endTime).toMinutes())
            .isEqualTo(119)
        assertThat(created.last().dayNumber).isEqualTo(2L)
    }

    @Test
    @DisplayName("2일차부터는 첫 계획 이전과 계획 사이와 마지막 계획 이후를 모두 자유시간으로 저장한다")
    fun createsAllFreeTimeGapsAfterFirstDay() {
        val startDate = LocalDate.of(2026, 8, 6)
        val travelDate = startDate.plusDays(1)
        val tripGroup = createTripGroup(
            startDate = startDate,
            nights = 1,
        )
        tripGroupSettingsRepository.save(
            TripGroupSettings(
                tripGroup = tripGroup,
                freeTimeMinutesByDay = mutableMapOf(2 to 60),
            ),
        )
        timelineRepository.saveAll(
            listOf(
                Timeline.create(
                    tripGroup = tripGroup,
                    dayNumber = 2L,
                    startTime = travelDate.atTime(9, 0),
                    endTime = travelDate.atTime(10, 0),
                ),
                Timeline.create(
                    tripGroup = tripGroup,
                    dayNumber = 2L,
                    startTime = travelDate.atTime(12, 0),
                    endTime = travelDate.atTime(13, 0),
                ),
            ),
        )

        val created = timelineFreeTimeService.createForDate(
            tripGroupId = requireNotNull(tripGroup.id),
            travelDate = travelDate,
        )

        assertThat(created).hasSize(22)
        assertThat(created.first().startTime).isEqualTo(travelDate.atStartOfDay())
        assertThat(created[8].endTime).isEqualTo(travelDate.atTime(9, 0))
        assertThat(created[9].startTime).isEqualTo(travelDate.atTime(10, 0))
        assertThat(created[10].endTime).isEqualTo(travelDate.atTime(12, 0))
        assertThat(created[11].startTime).isEqualTo(travelDate.atTime(13, 0))
        assertThat(created.last().endTime).isEqualTo(travelDate.atTime(23, 59))
    }

    private fun createTripGroup(
        startDate: LocalDate,
        nights: Int = 0,
    ): TripGroup {
        val owner = memberRepository.findById(1L).orElseThrow()
        return tripGroupRepository.save(
            TripGroup(
                owner = owner,
                name = "자유시간 테스트 여행",
                region = "서울",
                nights = nights,
                joinCode = UUID.randomUUID().toString().take(7),
                startDate = startDate,
                endDate = startDate.plusDays(nights.toLong()),
            ),
        )
    }
}
