package csh.back.domain.trip.group.controller

import com.fasterxml.jackson.databind.ObjectMapper
import csh.back.domain.member.dto.response.AuthFilterDto
import csh.back.domain.trip.group.service.TripGroupService
import csh.back.domain.trip.group.support.WithMockLoginUser
import org.hamcrest.Matchers
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
// invite 등 쓰기 API가 공유 H2 DB에 데이터를 남겨 다른 테스트(예: TripMemberV1ControllerTest)를 오염시키므로 각 테스트를 롤백
@Transactional
class TripGroupV1ControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var tripGroupService: TripGroupService

    private val BASE_URL = "/api/v1"

    @WithMockLoginUser
    fun t1() {
        val resultActions = mvc
            .perform(get("$BASE_URL/trips"))
            .andDo(print())

        val member = SecurityContextHolder.getContext()
            .authentication!!
            .principal as AuthFilterDto

        val tripGroups = tripGroupService.getGroups(member.id, "", "")

        resultActions
            .andExpect(handler().handlerType(TripGroupV1Controller::class.java))
            .andExpect(handler().methodName("getAllGroups"))
            .andExpect(status().isOk)

        for (i in tripGroups.indices) {
            val trip = tripGroups[i]
            resultActions.andExpect(jsonPath("$.data[$i].ownerId").value(trip.ownerId as Any))
        }
    }

    @Test
    @DisplayName("user 정보 없이 모임방 조회")
    fun t2() {
        val resultActions = mvc
            .perform(get("$BASE_URL/trips"))
            .andDo(print())

        resultActions
            .andExpect(status().isForbidden)
    }

    @Test
    @DisplayName("모임방 생성")
    @WithMockLoginUser
    fun t3() {
        val owner = SecurityContextHolder.getContext()
            .authentication!!
            .principal as AuthFilterDto

        val resultActions = mvc
            .perform(
                post("$BASE_URL/trips")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "name": "test travel",
                            "region" : "test region",
                            "startDate" : "2026-07-01",
                            "nights": 4
                        }
                        """.trimIndent()
                    )
            )
            .andDo(print())

        val body = resultActions.andReturn().response.contentAsString
        val id = ObjectMapper().readTree(body).get("data").get("id").asLong()

        val tripGroup = tripGroupService.getGroupDetail(id, owner.id)

        resultActions
            .andExpect(handler().handlerType(TripGroupV1Controller::class.java))
            .andExpect(handler().methodName("saveGroup"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.id").value(tripGroup.id as Any))
            .andExpect(jsonPath("$.data.name").value(tripGroup.name as Any))
            .andExpect(jsonPath("$.data.ownerId").value(tripGroup.ownerId as Any))
            .andExpect(jsonPath("$.data.region").value(tripGroup.region as Any))
            .andExpect(jsonPath("$.data.joinCode").value(tripGroup.joinCode as Any))
            .andExpect(jsonPath("$.data.nights").value(tripGroup.nights as Any))
            .andExpect(jsonPath("$.data.startDate").value<String>(Matchers.startsWith(tripGroup.startDate.toString())))
            .andExpect(jsonPath("$.data.endDate").value<String>(Matchers.startsWith(tripGroup.endDate.toString())))
    }

    @Test
    @DisplayName("모임방 생성 with 존재 하지 않는 사용자")
    @WithMockLoginUser(id = 10L, email = "excep@excep.com")
    fun t4() {
        val resultActions = mvc
            .perform(
                post("$BASE_URL/trips")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "name": "test travel",
                            "region" : "test region",
                            "startDate" : "2026-07-01",
                            "nights": 4
                        }
                        """.trimIndent()
                    )
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(TripGroupV1Controller::class.java))
            .andExpect(handler().methodName("saveGroup"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("존재하지 않는 유저"))
    }

    @Test
    @DisplayName("모임방 생성 with request 필드 중 하나가 전달되지 않은 경우")
    @WithMockLoginUser
    fun t5() {
        val resultActions = mvc
            .perform(
                post("$BASE_URL/trips")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "name": "test travel",
                            "region" : "test region",
                            "startDate" : "2026-07-01"
                        }
                        """.trimIndent()
                    )
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(TripGroupV1Controller::class.java))
            .andExpect(handler().methodName("saveGroup"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("nights: must not be null"))
    }

    @Test
    @DisplayName("모임방 상세 조회")
    @WithMockLoginUser
    fun t6() {
        val id = 1L

        val owner = SecurityContextHolder.getContext()
            .authentication!!
            .principal as AuthFilterDto

        val resultActions = mvc
            .perform(get("$BASE_URL/trips/$id"))
            .andDo(print())

        val tripGroup = tripGroupService.getGroupDetail(id, owner.id)

        resultActions
            .andExpect(handler().handlerType(TripGroupV1Controller::class.java))
            .andExpect(handler().methodName("getGroupDetail"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id").value(tripGroup.id as Any))
            .andExpect(jsonPath("$.data.name").value(tripGroup.name as Any))
            .andExpect(jsonPath("$.data.ownerId").value(tripGroup.ownerId as Any))
            .andExpect(jsonPath("$.data.region").value(tripGroup.region as Any))
            .andExpect(jsonPath("$.data.joinCode").value(tripGroup.joinCode as Any))
            .andExpect(jsonPath("$.data.nights").value(tripGroup.nights as Any))
            .andExpect(jsonPath("$.data.startDate").value<String>(Matchers.startsWith(tripGroup.startDate.toString())))
            .andExpect(jsonPath("$.data.endDate").value<String>(Matchers.startsWith(tripGroup.endDate.toString())))

        for (i in tripGroup.members.indices) {
            resultActions
                .andExpect(jsonPath("$.data.members[$i].memberId").value(tripGroup.members[i].memberId as Any))
                .andExpect(jsonPath("$.data.members[$i].name").value(tripGroup.members[i].name as Any))
        }
    }

    @Test
    @DisplayName("모임방 상세 조회 with 참여자가 아닌 경우")
    @WithMockLoginUser(id = 2L, email = "member2@admin.com")
    fun t7() {
        val id = 1L

        val resultActions = mvc
            .perform(get("$BASE_URL/trips/$id"))
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(TripGroupV1Controller::class.java))
            .andExpect(handler().methodName("getGroupDetail"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("해당 모임의 멤버가 아닙니다."))
    }

    @Test
    @DisplayName("모임방 상세 조회 with 존재하지 않는 모임")
    @WithMockLoginUser
    fun t8() {
        val id = 10L

        val resultActions = mvc
            .perform(get("$BASE_URL/trips/$id"))
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(TripGroupV1Controller::class.java))
            .andExpect(handler().methodName("getGroupDetail"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("존재하지 않는 모임입니다."))
    }

    @Test
    @DisplayName("모임방 상세 조회 수정")
    @WithMockLoginUser
    fun t9() {
        val id = 1L

        val owner = SecurityContextHolder.getContext()
            .authentication!!
            .principal as AuthFilterDto

        val resultActions = mvc
            .perform(
                patch("$BASE_URL/trips/$id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "name": "테스트 제목 수정"
                        }
                        """.trimIndent()
                    )
            )
            .andDo(print())

        val tripGroup = tripGroupService.getGroupDetail(id, owner.id)

        resultActions
            .andExpect(handler().handlerType(TripGroupV1Controller::class.java))
            .andExpect(handler().methodName("modifyGroupName"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id").value(tripGroup.id as Any))
            .andExpect(jsonPath("$.data.name").value(tripGroup.name as Any))
            .andExpect(jsonPath("$.data.ownerId").value(tripGroup.ownerId as Any))
            .andExpect(jsonPath("$.data.region").value(tripGroup.region as Any))
            .andExpect(jsonPath("$.data.joinCode").value(tripGroup.joinCode as Any))
            .andExpect(jsonPath("$.data.nights").value(tripGroup.nights as Any))
            .andExpect(jsonPath("$.data.startDate").value<String>(Matchers.startsWith(tripGroup.startDate.toString())))
            .andExpect(jsonPath("$.data.endDate").value<String>(Matchers.startsWith(tripGroup.endDate.toString())))
    }

    @Test
    @DisplayName("모임방 상세 조회 수정 with 모임 소유자가 아닌 사용자 접근")
    @WithMockLoginUser(id = 2L, email = "memer2@admin.com")
    fun t10() {
        val id = 1L

        val resultActions = mvc
            .perform(
                patch("$BASE_URL/trips/$id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "name": "테스트 제목 수정"
                        }
                        """.trimIndent()
                    )
            )
            .andDo(print())

        resultActions
            .andExpect(handler().handlerType(TripGroupV1Controller::class.java))
            .andExpect(handler().methodName("modifyGroupName"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("해당 모임의 소유자가 아닙니다."))
    }

    // ========== 지난 메이트 초대 (invite) ==========
    // TestInitData:
    //   - tg1 (owner=admin): [admin, member3]
    //   - tg2 (owner=member2): [member2, member3]
    //   - tg3 (owner=admin): [admin]

    @Test
    @DisplayName("초대 - 방장(admin)이 member2를 tg3에 초대 → 200, 1건 추가")
    @WithMockLoginUser
    fun t11() {
        mvc.perform(
            post("$BASE_URL/trips/3/members/invite")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "memberIds": [2] }""")
        )
            .andDo(print())
            .andExpect(handler().handlerType(TripGroupV1Controller::class.java))
            .andExpect(handler().methodName("inviteMembers"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].memberId").value(2))
            .andExpect(jsonPath("$.data[0].name").value("member2"))
            .andExpect(jsonPath("$.data[0].admin").value(false))
    }

    @Test
    @DisplayName("초대 - 방장 아닌 member3이 tg1에 초대 시도 → 403")
    @WithMockLoginUser(id = 3L, email = "member3@admin.com")
    fun t12() {
        mvc.perform(
            post("$BASE_URL/trips/1/members/invite")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "memberIds": [2] }""")
        )
            .andDo(print())
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("해당 모임의 소유자가 아닙니다."))
    }

    @Test
    @DisplayName("초대 - 이미 멤버인 member3을 tg1에 초대 → 200, 빈 배열 (스킵)")
    @WithMockLoginUser
    fun t13() {
        mvc.perform(
            post("$BASE_URL/trips/1/members/invite")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "memberIds": [3] }""")
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(0))
    }

    @Test
    @DisplayName("초대 - 존재하지 않는 memberId 스킵 → 200, 빈 배열")
    @WithMockLoginUser
    fun t14() {
        mvc.perform(
            post("$BASE_URL/trips/3/members/invite")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "memberIds": [9999] }""")
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(0))
    }

    @Test
    @DisplayName("초대 - 존재하지 않는 여행방 → 404")
    @WithMockLoginUser
    fun t15() {
        mvc.perform(
            post("$BASE_URL/trips/9999/members/invite")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "memberIds": [2] }""")
        )
            .andDo(print())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("존재하지 않는 모임입니다."))
    }

    @Test
    @DisplayName("초대 - memberIds가 빈 배열이면 검증 실패 → 400")
    @WithMockLoginUser
    fun t16() {
        mvc.perform(
            post("$BASE_URL/trips/3/members/invite")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "memberIds": [] }""")
        )
            .andDo(print())
            .andExpect(status().isBadRequest)
    }
}
