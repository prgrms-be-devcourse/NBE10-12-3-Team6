package csh.back.domain.vote.vote.controller

import csh.back.domain.trip.group.support.WithMockLoginUser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

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
class VoteV1ControllerAccessTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Test
    @DisplayName("존재하지 않는 여행방의 투표 조회는 404를 반환한다")
    @WithMockLoginUser
    fun rejectsMissingTripGroup() {
        mvc.perform(get(BASE_URL, MISSING_TRIP_ID))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.statusCode").value(404))
            .andExpect(jsonPath("$.message").value("존재하지 않는 모임입니다."))
    }

    @Test
    @DisplayName("여행방 비멤버의 투표 조회는 403을 반환한다")
    @WithMockLoginUser(id = NON_MEMBER_ID, email = "member2@admin.com")
    fun rejectsNonMember() {
        mvc.perform(get(BASE_URL, TRIP_ID))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.statusCode").value(403))
            .andExpect(jsonPath("$.message").value("해당 모임의 멤버가 아닙니다."))
    }

    private companion object {
        const val BASE_URL = "/api/v1/trips/{tripGroupId}/votes"
        const val TRIP_ID = 1L
        const val MISSING_TRIP_ID = 999L
        const val NON_MEMBER_ID = 2L
    }
}
