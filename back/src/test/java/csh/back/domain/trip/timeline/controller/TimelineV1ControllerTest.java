package csh.back.domain.trip.timeline.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import csh.back.domain.member.dto.response.AuthFilterDto;
import csh.back.domain.trip.group.support.WithMockLoginUser;
import csh.back.domain.trip.timeline.repository.TimelineRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TimelineV1ControllerTest {

    private static final String BASE_URL = "/api/v1/trips/{tripId}/timelines";
    private static final Long TRIP_ID = 1L;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TimelineRepository timelineRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setAuthenticationDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AuthFilterDto member) {
            token.setDetails(member.id());
        }
    }

    @Test
    @DisplayName("타임라인 단건 생성")
    @WithMockLoginUser
    void t1() throws Exception {
        ResultActions resultActions = mvc.perform(
                        post(BASE_URL, TRIP_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "dayNumber": 1,
                                            "startTime": "2026-07-01T09:00:00",
                                            "endTime": "2026-07-01T10:00:00"
                                        }
                                        """)
                )
                .andDo(print());

        resultActions
                .andExpect(handler().handlerType(TimelineV1Controller.class))
                .andExpect(handler().methodName("createTimeline"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.timelineId").isNumber())
                .andExpect(jsonPath("$.data.dayNumber").value(1))
                .andExpect(jsonPath("$.data.startTime").value("2026-07-01T09:00:00"))
                .andExpect(jsonPath("$.data.endTime").value("2026-07-01T10:00:00"));
    }

    @Test
    @DisplayName("타임라인 생성 실패 - 일차가 1 미만인 경우")
    @WithMockLoginUser
    void t2() throws Exception {
        ResultActions resultActions = mvc.perform(
                        post(BASE_URL, TRIP_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "dayNumber": 0,
                                            "startTime": "2026-07-01T09:00:00",
                                            "endTime": "2026-07-01T10:00:00"
                                        }
                                        """)
                )
                .andDo(print());

        resultActions
                .andExpect(handler().handlerType(TimelineV1Controller.class))
                .andExpect(handler().methodName("createTimeline"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("dayNumber: must be greater than or equal to 1"));
    }

    @Test
    @DisplayName("타임라인 일괄 생성")
    @WithMockLoginUser
    void t3() throws Exception {
        ResultActions resultActions = mvc.perform(
                        post(BASE_URL + "/batch", TRIP_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
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
                                        """)
                )
                .andDo(print());

        resultActions
                .andExpect(handler().handlerType(TimelineV1Controller.class))
                .andExpect(handler().methodName("createAllTimelines"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].dayNumber").value(1))
                .andExpect(jsonPath("$.data[1].dayNumber").value(1));
    }

    @Test
    @DisplayName("일차별 타임라인 목록 조회")
    @WithMockLoginUser
    void t4() throws Exception {
        Long timelineId = createTimeline("2026-07-01T09:00:00", "2026-07-01T10:00:00");

        ResultActions resultActions = mvc.perform(
                        get(BASE_URL, TRIP_ID)
                                .queryParam("dayNumber", "1")
                )
                .andDo(print());

        resultActions
                .andExpect(handler().handlerType(TimelineV1Controller.class))
                .andExpect(handler().methodName("getTimelines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].timelineId").value(timelineId))
                .andExpect(jsonPath("$.data[0].dayNumber").value(1));
    }

    @Test
    @DisplayName("타임라인 시간 수정")
    @WithMockLoginUser
    void t5() throws Exception {
        Long timelineId = createTimeline("2026-07-01T09:00:00", "2026-07-01T10:00:00");

        ResultActions resultActions = mvc.perform(
                        patch(BASE_URL + "/{timelineId}", TRIP_ID, timelineId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "startTime": "2026-07-01T09:30:00",
                                            "endTime": "2026-07-01T10:30:00"
                                        }
                                        """)
                )
                .andDo(print());

        resultActions
                .andExpect(handler().handlerType(TimelineV1Controller.class))
                .andExpect(handler().methodName("updateTimeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timelineId").value(timelineId))
                .andExpect(jsonPath("$.data.startTime").value("2026-07-01T09:30:00"))
                .andExpect(jsonPath("$.data.endTime").value("2026-07-01T10:30:00"));
    }

    @Test
    @DisplayName("타임라인 삭제")
    @WithMockLoginUser
    void t6() throws Exception {
        Long timelineId = createTimeline("2026-07-01T09:00:00", "2026-07-01T10:00:00");
        entityManager.flush();
        entityManager.clear();

        ResultActions resultActions = mvc.perform(
                        delete(BASE_URL + "/{timelineId}", TRIP_ID, timelineId)
                )
                .andDo(print());

        resultActions
                .andExpect(handler().handlerType(TimelineV1Controller.class))
                .andExpect(handler().methodName("deleteTimeline"))
                .andExpect(status().isOk());

        assertFalse(timelineRepository.existsById(timelineId));
    }

    @Test
    @DisplayName("인증 정보 없이 타임라인 조회")
    void t7() throws Exception {
        mvc.perform(
                        get(BASE_URL, TRIP_ID)
                                .queryParam("dayNumber", "1")
                )
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    private Long createTimeline(String startTime, String endTime) throws Exception {
        MvcResult result = mvc.perform(
                        post(BASE_URL, TRIP_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "dayNumber": 1,
                                            "startTime": "%s",
                                            "endTime": "%s"
                                        }
                                        """.formatted(startTime, endTime))
                )
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("timelineId")
                .asLong();
    }
}
