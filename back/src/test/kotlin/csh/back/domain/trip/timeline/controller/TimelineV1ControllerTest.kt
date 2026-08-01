package csh.back.domain.trip.timeline.controller

import com.fasterxml.jackson.databind.ObjectMapper
import csh.back.domain.member.dto.response.AuthFilterDto
import csh.back.domain.trip.group.support.WithMockLoginUser
import csh.back.domain.trip.post.service.S3UploadService
import csh.back.domain.trip.timeline.repository.TimelineRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@ActiveProfiles("test")
@SpringBootTest(
    properties = [
        "cloud.aws.credentials.access-key=test",
        "cloud.aws.credentials.secret-key=test",
        "cloud.aws.region.static=ap-northeast-2",
        "cloud.aws.s3.endpoint=http://localhost",
    ],
)
@AutoConfigureMockMvc
@Transactional
class TimelineV1ControllerTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var timelineRepository: TimelineRepository

    @MockitoBean
    private lateinit var s3UploadService: S3UploadService

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setAuthenticationDetails() {
        val authentication = SecurityContextHolder.getContext().authentication

        if (
            authentication is UsernamePasswordAuthenticationToken &&
            authentication.principal is AuthFilterDto
        ) {
            val member = authentication.principal as AuthFilterDto
            authentication.details = member.id
        }
    }

    @Test
    @DisplayName("타임라인 단건 생성")
    @WithMockLoginUser
    fun t1() {
        val resultActions = mvc.perform(
            post(BASE_URL, TRIP_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "dayNumber": 1,
                        "startTime": "2026-07-01T09:00:00",
                        "endTime": "2026-07-01T10:00:00"
                    }
                    """.trimIndent(),
                ),
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(TimelineV1Controller::class.java))
            .andExpect(handler().methodName("createTimeline"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.timelineId").isNumber)
            .andExpect(jsonPath("$.data.dayNumber").value(1))
            .andExpect(jsonPath("$.data.startTime").value("2026-07-01T09:00:00"))
            .andExpect(jsonPath("$.data.endTime").value("2026-07-01T10:00:00"))
    }

    @Test
    @DisplayName("타임라인 생성 실패 - 일차가 1 미만인 경우")
    @WithMockLoginUser
    fun t2() {
        val resultActions = mvc.perform(
            post(BASE_URL, TRIP_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "dayNumber": 0,
                        "startTime": "2026-07-01T09:00:00",
                        "endTime": "2026-07-01T10:00:00"
                    }
                    """.trimIndent(),
                ),
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(TimelineV1Controller::class.java))
            .andExpect(handler().methodName("createTimeline"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("dayNumber: must be greater than or equal to 1"))
    }

    @Test
    @DisplayName("타임라인 일괄 생성")
    @WithMockLoginUser
    fun t3() {
        val resultActions = mvc.perform(
            post("$BASE_URL/batch", TRIP_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "dayNumber": 1,
                        "timelines": [
                            {
                                "dayNumber": 1,
                                "startTime": "2026-07-01T09:00:00",
                                "endTime": "2026-07-01T10:00:00"
                            },
                            {
                                "dayNumber": 1,
                                "startTime": "2026-07-01T10:00:00",
                                "endTime": "2026-07-01T11:00:00"
                            }
                        ]
                    }
                    """.trimIndent(),
                ),
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(TimelineV1Controller::class.java))
            .andExpect(handler().methodName("createAllTimelines"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data", hasSize<Any>(2)))
            .andExpect(jsonPath("$.data[0].dayNumber").value(1))
            .andExpect(jsonPath("$.data[1].dayNumber").value(1))
    }

    @Test
    @DisplayName("일차별 타임라인 목록 조회")
    @WithMockLoginUser
    fun t4() {
        val timelineId = createTimeline(
            startTime = "2026-07-01T09:00:00",
            endTime = "2026-07-01T10:00:00",
        )
        val resultActions = mvc.perform(
            get(BASE_URL, TRIP_ID)
                .queryParam("dayNumber", "1"),
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(TimelineV1Controller::class.java))
            .andExpect(handler().methodName("getTimelines"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data", hasSize<Any>(1)))
            .andExpect(jsonPath("$.data[0].timelineId").value(timelineId))
            .andExpect(jsonPath("$.data[0].dayNumber").value(1))
    }

    @Test
    @DisplayName("타임라인 시간 수정")
    @WithMockLoginUser
    fun t5() {
        val timelineId = createTimeline(
            startTime = "2026-07-01T09:00:00",
            endTime = "2026-07-01T10:00:00",
        )
        val resultActions = mvc.perform(
            patch("$BASE_URL/{timelineId}", TRIP_ID, timelineId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "startTime": "2026-07-01T09:30:00",
                        "endTime": "2026-07-01T10:30:00"
                    }
                    """.trimIndent(),
                ),
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(TimelineV1Controller::class.java))
            .andExpect(handler().methodName("updateTimeline"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.timelineId").value(timelineId))
            .andExpect(jsonPath("$.data.startTime").value("2026-07-01T09:30:00"))
            .andExpect(jsonPath("$.data.endTime").value("2026-07-01T10:30:00"))
    }

    @Test
    @DisplayName("타임라인 삭제")
    @WithMockLoginUser
    fun t6() {
        val timelineId = createTimeline(
            startTime = "2026-07-01T09:00:00",
            endTime = "2026-07-01T10:00:00",
        )
        entityManager.flush()
        entityManager.clear()

        val resultActions = mvc.perform(
            delete("$BASE_URL/{timelineId}", TRIP_ID, timelineId),
        ).andDo(print())

        resultActions
            .andExpect(handler().handlerType(TimelineV1Controller::class.java))
            .andExpect(handler().methodName("deleteTimeline"))
            .andExpect(status().isOk)

        assertFalse(timelineRepository.existsById(timelineId))
    }

    @Test
    @DisplayName("인증 정보 없이 타임라인 조회")
    fun t7() {
        mvc.perform(
            get(BASE_URL, TRIP_ID)
                .queryParam("dayNumber", "1"),
        )
            .andDo(print())
            .andExpect(status().isForbidden)
    }

    @Test
    @DisplayName("존재하지 않는 여행방의 타임라인 조회는 404 반환")
    @WithMockLoginUser
    fun rejectsMissingTripGroup() {
        mvc.perform(get("$BASE_URL/count", MISSING_TRIP_ID))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.statusCode").value(404))
            .andExpect(jsonPath("$.message").value("존재하지 않는 모임입니다."))
    }

    @Test
    @DisplayName("여행방 비멤버의 타임라인 조회는 403 반환")
    @WithMockLoginUser(id = NON_MEMBER_ID, email = "member2@admin.com")
    fun rejectsNonMember() {
        mvc.perform(get("$BASE_URL/count", TRIP_ID))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.statusCode").value(403))
            .andExpect(jsonPath("$.message").value("해당 모임의 멤버가 아닙니다."))
    }

    private fun createTimeline(startTime: String, endTime: String): Long {
        val result = mvc.perform(
            post(BASE_URL, TRIP_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "dayNumber": 1,
                        "startTime": "$startTime",
                        "endTime": "$endTime"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readTree(result.response.contentAsString)
            .path("data")
            .path("timelineId")
            .asLong()
    }

    companion object {
        private const val BASE_URL = "/api/v1/trips/{tripId}/timelines"
        private const val TRIP_ID = 1L
        private const val MISSING_TRIP_ID = 999L
        private const val NON_MEMBER_ID = 2L
    }
}
