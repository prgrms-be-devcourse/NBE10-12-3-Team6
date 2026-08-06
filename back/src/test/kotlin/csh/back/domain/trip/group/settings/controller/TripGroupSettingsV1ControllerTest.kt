package csh.back.domain.trip.group.settings.controller

import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.member.support.WithMockMember
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.group.settings.repository.TripGroupSettingsRepository
import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.member.repository.TripMemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TripGroupSettingsV1ControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var tripGroupRepository: TripGroupRepository

    @Autowired
    lateinit var tripMemberRepository: TripMemberRepository

    @Autowired
    lateinit var tripGroupSettingsRepository: TripGroupSettingsRepository

    private var futureTripId: Long = 0L

    @BeforeEach
    fun setUp() {
        futureTripId = createTrip(SEOUL_TODAY.plusDays(1))
    }

    @Test
    @DisplayName("여행방 멤버는 자유시간 설정을 조회할 수 있다")
    @WithMockMember(id = 3L, email = "member3@admin.com")
    fun getSettingsAsTripMember() {
        mvc.perform(get("$BASE_URL/trips/1/settings"))
            .andExpect(handler().handlerType(TripGroupSettingsV1Controller::class.java))
            .andExpect(handler().methodName("getSettings"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.tripGroupId").value(1))
            .andExpect(jsonPath("$.data.days.length()").value(3))
            .andExpect(jsonPath("$.data.days[0].dayNumber").value(1))
            .andExpect(jsonPath("$.data.days[0].freeTimeMinutes").value(60))
            .andExpect(jsonPath("$.data.editable").value(false))
    }

    @Test
    @DisplayName("여행방 방장은 일차별 자유시간 설정을 30분 단위로 수정할 수 있다")
    @WithMockMember
    fun updateFreeTimeMinutesAsOwner() {
        mvc.perform(
            patch("$BASE_URL/trips/$futureTripId/settings/free-time/2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"freeTimeMinutes": 90}"""),
        )
            .andExpect(handler().handlerType(TripGroupSettingsV1Controller::class.java))
            .andExpect(handler().methodName("updateFreeTimeMinutes"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.tripGroupId").value(futureTripId))
            .andExpect(jsonPath("$.data.days[0].freeTimeMinutes").value(60))
            .andExpect(jsonPath("$.data.days[1].dayNumber").value(2))
            .andExpect(jsonPath("$.data.days[1].freeTimeMinutes").value(90))
            .andExpect(jsonPath("$.data.editable").value(true))
    }

    @Test
    @DisplayName("여행방 방장은 모든 일차의 자유시간 설정을 한 번에 수정할 수 있다")
    @WithMockMember
    fun updateAllFreeTimeMinutesAsOwner() {
        mvc.perform(
            patch("$BASE_URL/trips/$futureTripId/settings/free-time")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"freeTimeMinutes": 90}"""),
        )
            .andExpect(handler().handlerType(TripGroupSettingsV1Controller::class.java))
            .andExpect(handler().methodName("updateAllFreeTimeMinutes"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.tripGroupId").value(futureTripId))
            .andExpect(jsonPath("$.data.days.length()").value(3))
            .andExpect(jsonPath("$.data.days[0].freeTimeMinutes").value(90))
            .andExpect(jsonPath("$.data.days[1].freeTimeMinutes").value(90))
            .andExpect(jsonPath("$.data.days[2].freeTimeMinutes").value(90))
            .andExpect(jsonPath("$.data.editable").value(true))
    }

    @Test
    @DisplayName("30분 단위가 아닌 자유시간 설정은 거부한다")
    @WithMockMember
    fun rejectInvalidFreeTimeMinutes() {
        mvc.perform(
            patch("$BASE_URL/trips/$futureTripId/settings/free-time/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"freeTimeMinutes": 45}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("자유시간은 30분 단위로 30분 이상 3시간 이하이어야 합니다."))
    }

    @Test
    @DisplayName("방장이 아닌 멤버는 자유시간 설정을 수정할 수 없다")
    @WithMockMember(id = 3L, email = "member3@admin.com")
    fun rejectUpdateFromNonOwner() {
        mvc.perform(
            patch("$BASE_URL/trips/$futureTripId/settings/free-time/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"freeTimeMinutes": 120}"""),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("해당 모임의 소유자만 설정을 변경할 수 있습니다."))
    }

    @Test
    @DisplayName("여행방 멤버가 아니면 설정을 조회할 수 없다")
    @WithMockMember(id = 2L, email = "member2@admin.com")
    fun rejectSettingsReadFromNonMember() {
        mvc.perform(get("$BASE_URL/trips/1/settings"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("해당 모임의 멤버가 아닙니다."))
    }

    @Test
    @DisplayName("여행 기간에 없는 일차의 자유시간 설정은 수정할 수 없다")
    @WithMockMember
    fun rejectUpdateForUnknownDay() {
        mvc.perform(
            patch("$BASE_URL/trips/$futureTripId/settings/free-time/4")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"freeTimeMinutes": 120}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("존재하지 않는 여행 일차입니다."))
    }

    @Test
    @DisplayName("여행 시작일부터 자유시간 설정을 수정할 수 없다")
    @WithMockMember
    fun rejectUpdateFromTripStartDate() {
        val startedTripId = createTrip(SEOUL_TODAY)

        mvc.perform(
            patch("$BASE_URL/trips/$startedTripId/settings/free-time/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"freeTimeMinutes": 120}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("여행 시작일부터 자유시간 범위를 변경할 수 없습니다."))
    }

    @Test
    @DisplayName("여행 시작일부터 방장에게도 자유시간 설정을 수정 불가로 응답한다")
    @WithMockMember
    fun markSettingsAsNotEditableFromTripStartDate() {
        val startedTripId = createTrip(SEOUL_TODAY)

        mvc.perform(get("$BASE_URL/trips/$startedTripId/settings"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.editable").value(false))
    }

    private fun createTrip(startDate: LocalDate): Long {
        val owner = memberRepository.findById(1L).orElseThrow()
        val tripGroup = tripGroupRepository.save(
            TripGroup(
                owner = owner,
                name = "자유시간 설정 테스트 여행",
                region = "부산",
                nights = 2,
                joinCode = UUID.randomUUID().toString(),
                startDate = startDate,
                endDate = startDate.plusDays(2),
            ),
        )
        tripMemberRepository.save(
            TripMember(
                member = owner,
                tripGroup = tripGroup,
                isAdmin = true,
            ),
        )

        return requireNotNull(tripGroup.id)
    }

    @Test
    @DisplayName("익명/실명 투표 설정 변경 - 200")
    @WithMockMember
    fun updateAnonymousVote() {
        mvc.perform(
            patch("$BASE_URL/trips/$futureTripId/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "isAnonymousVote": false
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(handler().handlerType(TripGroupSettingsV1Controller::class.java))
            .andExpect(handler().methodName("updateAnonymousVote"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isAnonymousVote").value(false))

        val saved = tripGroupSettingsRepository.findByTripGroupId(futureTripId).orElseThrow()
        assertThat(saved.isAnonymousVote).isFalse()
    }

    @Test
    @DisplayName("여행 시작일부터 익명 투표 설정을 수정할 수 없다")
    @WithMockMember
    fun rejectUpdateAnonymousVoteFromTripStartDate() {
        val startedTripId = createTrip(SEOUL_TODAY)

        mvc.perform(
            patch("$BASE_URL/trips/$startedTripId/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "isAnonymousVote": false
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("여행 시작일부터 익명 투표 설정을 변경할 수 없습니다."))
    }

    private companion object {
        const val BASE_URL = "/api/v1"
        val SEOUL_TODAY: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))
    }
}
