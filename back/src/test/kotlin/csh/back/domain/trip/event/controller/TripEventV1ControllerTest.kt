package csh.back.domain.trip.event.controller

import csh.back.domain.trip.event.service.TripEventService
import csh.back.domain.trip.group.support.WithMockLoginUser
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.nio.charset.StandardCharsets.UTF_8

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
class TripEventV1ControllerTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @MockitoBean
    private lateinit var tripEventService: TripEventService

    @Test
    @DisplayName("인증된 사용자는 여행방 SSE API를 구독하고 text/event-stream 응답을 받는다")
    @WithMockLoginUser(id = MEMBER_ID)
    fun subscribesThroughController() {
        val emitter = completedConnectedEmitter()
        Mockito.doReturn(emitter)
            .`when`(tripEventService)
            .subscribe(TRIP_GROUP_ID, MEMBER_ID)

        val initialResult = mvc.perform(
            get(BASE_URL, TRIP_GROUP_ID)
                .accept(MediaType.TEXT_EVENT_STREAM),
        )
            .andExpect(handler().handlerType(TripEventV1Controller::class.java))
            .andExpect(handler().methodName("subscribe"))
            .andExpect(request().asyncStarted())
            .andReturn()

        val completedResult = mvc.perform(asyncDispatch(initialResult))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andExpect(content().string(containsString("event:CONNECTED")))
            .andReturn()

        val responseBody = String(completedResult.response.contentAsByteArray, UTF_8)
        assertTrue(responseBody.contains("여행방 변경 알림 연결이 완료되었습니다."))

        Mockito.verify(tripEventService).subscribe(TRIP_GROUP_ID, MEMBER_ID)
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 여행방 SSE API를 구독할 수 없다")
    fun rejectsUnauthenticatedSubscriber() {
        mvc.perform(
            get(BASE_URL, TRIP_GROUP_ID)
                .accept(MediaType.TEXT_EVENT_STREAM),
        )
            .andExpect(status().isForbidden)

        Mockito.verifyNoInteractions(tripEventService)
    }

    private fun completedConnectedEmitter(): SseEmitter =
        SseEmitter().apply {
            send(
                SseEmitter.event()
                    .name("CONNECTED")
                    .data("여행방 변경 알림 연결이 완료되었습니다."),
            )
            complete()
        }

    private companion object {
        const val BASE_URL = "/api/v1/trips/{tripGroupId}/events/subscribe"
        const val TRIP_GROUP_ID = 1L
        const val MEMBER_ID = 1L
    }
}
