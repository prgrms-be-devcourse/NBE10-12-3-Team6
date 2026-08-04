package csh.back.domain.trip.group.controller

import com.fasterxml.jackson.databind.ObjectMapper
import csh.back.domain.member.dto.response.AuthFilterDto
import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.group.service.TripGroupService
import csh.back.domain.trip.group.support.WithMockLoginUser
import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.member.repository.TripMemberRepository
import org.hamcrest.Matchers
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

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

    // 삭제 시나리오에서 미래 startDate 방을 즉석 생성하려고 리포지토리를 직접 주입.
    // (seed 데이터의 방들은 startDate가 과거 고정이라 삭제 성공 케이스를 만들 수 없음)
    @Autowired
    lateinit var tripGroupRepository: TripGroupRepository

    @Autowired
    lateinit var tripMemberRepository: TripMemberRepository

    @Autowired
    lateinit var memberRepository: MemberRepository

    private val BASE_URL = "/api/v1"

    // 삭제 테스트 전용: 지정한 startDate로 새 방을 만들고 방장(admin, id=1)을 첫 멤버로 등록.
    // @Transactional 롤백으로 시드 오염 없음. joinCode는 unique 컬럼이라 UUID로 충돌 회피.
    private fun createGroupForAdmin(startDate: LocalDate, nights: Int = 2): Long {
        val admin = memberRepository.findById(1L).get()
        val group = tripGroupRepository.save(
            TripGroup(
                owner = admin,
                name = "삭제테스트여행",
                region = "제주",
                nights = nights,
                joinCode = UUID.randomUUID().toString().replace("-", "").substring(0, 7),
                startDate = startDate,
                endDate = startDate.plusDays(nights.toLong()),
            ),
        )
        tripMemberRepository.save(TripMember(member = admin, tripGroup = group, isAdmin = true))
        return group.id!!
    }

    @WithMockLoginUser
    fun t1() {
        val resultActions = mvc
            .perform(get("$BASE_URL/trips"))
            .andDo(print())

        val member = SecurityContextHolder.getContext()
            .authentication!!
            .principal as AuthFilterDto

        val tripGroups = tripGroupService.getGroups(member.id, "", "", PageRequest.of(0, 10)).items

        resultActions
            .andExpect(handler().handlerType(TripGroupV1Controller::class.java))
            .andExpect(handler().methodName("getAllGroups"))
            .andExpect(status().isOk)

        for (i in tripGroups.indices) {
            val trip = tripGroups[i]
            resultActions.andExpect(jsonPath("$.data.items[$i].ownerId").value(trip.ownerId as Any))
        }
    }

    @Test
    @DisplayName("user 정보 없이 모임방 조회")
    fun t2() {
        val resultActions = mvc
            .perform(get("$BASE_URL/trips"))
            .andDo(print())

        resultActions
            .andExpect(status().isUnauthorized)
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

    // ========== 모임방 삭제 (단건/다중) ==========
    // 정책: 방장만 삭제 가능, 여행 시작 1일 이상 남은 방만 삭제 가능(TripGroupService.DELETE_ALLOWED_DAYS_BEFORE_START=1).
    // Soft delete: @SQLDelete + @SQLRestriction로 실제 DELETE 대신 deleted_at을 채우고 이후 조회에서 자동 제외.

    @Test
    @DisplayName("삭제 단건 - 방장이 시작 1일 이상 남은 방 삭제 → 200, 재조회 404")
    @WithMockLoginUser
    fun t17() {
        val tripId = createGroupForAdmin(startDate = LocalDate.now().plusDays(7))

        mvc.perform(delete("$BASE_URL/trips/$tripId"))
            .andDo(print())
            .andExpect(status().isOk)

        // @SQLRestriction 덕분에 soft-delete된 방은 findById가 empty → NotFoundException → 404.
        mvc.perform(get("$BASE_URL/trips/$tripId"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("존재하지 않는 모임입니다."))
    }

    @Test
    @DisplayName("삭제 단건 - 방장이라도 시작 당일 방은 삭제 불가 → 400")
    @WithMockLoginUser
    fun t18() {
        val tripId = createGroupForAdmin(startDate = LocalDate.now())

        mvc.perform(delete("$BASE_URL/trips/$tripId"))
            .andDo(print())
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("여행 시작 전날까지만 삭제할 수 있어요."))
    }

    @Test
    @DisplayName("삭제 단건 - 비방장이 삭제 시도 → 403 (시점 검증보다 방장 검증이 먼저)")
    @WithMockLoginUser(id = 2L, email = "member2@admin.com")
    fun t19() {
        // seed tg1은 admin(id=1) 소유. member2가 삭제 시도 → 방장 아니라 403.
        // startDate가 과거지만 방장 체크가 먼저 실행되므로 400이 아닌 403이 나와야 함.
        mvc.perform(delete("$BASE_URL/trips/1"))
            .andDo(print())
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("해당 모임의 소유자가 아닙니다."))
    }

    @Test
    @DisplayName("삭제 단건 - 존재하지 않는 방 → 404")
    @WithMockLoginUser
    fun t20() {
        mvc.perform(delete("$BASE_URL/trips/9999"))
            .andDo(print())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("존재하지 않는 모임입니다."))
    }

    @Test
    @DisplayName("삭제 단건 - 미인증 요청 → 401")
    fun t21() {
        mvc.perform(delete("$BASE_URL/trips/1"))
            .andDo(print())
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("다중 삭제 - 방장이 미래 방 2개 bulk-delete → 200, deletedCount=2")
    @WithMockLoginUser
    fun t22() {
        val tripId1 = createGroupForAdmin(startDate = LocalDate.now().plusDays(10))
        val tripId2 = createGroupForAdmin(startDate = LocalDate.now().plusDays(20))

        mvc.perform(
            post("$BASE_URL/trips/bulk-delete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ids":[$tripId1, $tripId2]}""")
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.deletedCount").value(2))

        // 두 방 모두 조회 시 404
        mvc.perform(get("$BASE_URL/trips/$tripId1")).andExpect(status().isNotFound)
        mvc.perform(get("$BASE_URL/trips/$tripId2")).andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("다중 삭제 - 하나가 정책 위반이면 all-or-nothing → 400")
    @WithMockLoginUser
    fun t23() {
        val futureId = createGroupForAdmin(startDate = LocalDate.now().plusDays(10))
        // 시작 당일 방 → 다중 삭제 시 TripDeletionNotAllowedException → 트랜잭션 롤백
        val todayId = createGroupForAdmin(startDate = LocalDate.now())

        mvc.perform(
            post("$BASE_URL/trips/bulk-delete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ids":[$futureId, $todayId]}""")
        )
            .andDo(print())
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("여행 시작 전날까지만 삭제할 수 있어요."))

        // 롤백 자체 검증은 여기서 안 함: 이 테스트 클래스가 @Transactional이라 서비스 호출과
        // 후속 mvc.perform이 동일 트랜잭션에서 돌아 실제 롤백은 테스트 종료 시점에나 반영됨.
        // 롤백 동작은 Spring @Transactional의 계약이라 별도 검증 불필요.
    }

    @Test
    @DisplayName("다중 삭제 - ids 빈 배열 → 400 (@NotEmpty 검증 실패)")
    @WithMockLoginUser
    fun t24() {
        mvc.perform(
            post("$BASE_URL/trips/bulk-delete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ids":[]}""")
        )
            .andDo(print())
            .andExpect(status().isBadRequest)
    }
}
