package csh.back.domain.trip.member.controller

import csh.back.domain.trip.group.support.WithMockLoginUser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * TestInitData 초기 상태 기준 검증:
 *   - member1(admin, id=1)의 지난 메이트: [member3]                  (tg1에서 함께)
 *   - member2(id=2)의 지난 메이트:         [member3]                  (tg2에서 함께)
 *   - member3(id=3)의 지난 메이트:         [member2, admin]           (최근순: tg2=2026-07-02, tg1=2026-07-01)
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
// 다른 테스트가 남긴 TripMember(예: 지난 메이트 초대)로 인해 기대값이 깨지지 않도록 각 테스트를 롤백
@Transactional
class TripMemberV1ControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    private val BASE_URL = "/api/v1"

    @Test
    @DisplayName("지난 메이트 조회 - admin 관점: 1명 (member3)")
    @WithMockLoginUser
    fun t1() {
        mvc.perform(get("$BASE_URL/trips/past-members"))
            .andDo(print())
            .andExpect(handler().handlerType(TripMemberV1Controller::class.java))
            .andExpect(handler().methodName("getPastMates"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(3))
            .andExpect(jsonPath("$.data.items[0].name").value("member3"))
            .andExpect(jsonPath("$.data.items[0].travelCount").value(1))
            .andExpect(jsonPath("$.data.hasNext").value(false))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(15))
    }

    @Test
    @DisplayName("지난 메이트 조회 - member3 관점: 2명 (최근순 member2 → admin)")
    @WithMockLoginUser(id = 3L, email = "member3@admin.com")
    fun t2() {
        mvc.perform(get("$BASE_URL/trips/past-members"))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()").value(2))
            // member2의 tg2가 최근이므로 먼저
            .andExpect(jsonPath("$.data.items[0].name").value("member2"))
            .andExpect(jsonPath("$.data.items[1].name").value("admin"))
    }

    @Test
    @DisplayName("지난 메이트 검색 - member3이 'admin' 검색 → admin 1건")
    @WithMockLoginUser(id = 3L, email = "member3@admin.com")
    fun t3() {
        mvc.perform(get("$BASE_URL/trips/past-members").param("search", "admin"))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].name").value("admin"))
    }

    @Test
    @DisplayName("지난 메이트 검색 - member3이 'member' 검색 → member2 1건 (admin은 매치 안 됨)")
    @WithMockLoginUser(id = 3L, email = "member3@admin.com")
    fun t4() {
        mvc.perform(get("$BASE_URL/trips/past-members").param("search", "member"))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].name").value("member2"))
    }

    @Test
    @DisplayName("지난 메이트 검색 - 매칭 없음 → 빈 결과, hasNext=false")
    @WithMockLoginUser
    fun t5() {
        mvc.perform(get("$BASE_URL/trips/past-members").param("search", "nomatch_xyz"))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()").value(0))
            .andExpect(jsonPath("$.data.hasNext").value(false))
    }

    @Test
    @DisplayName("지난 메이트 검색 - 빈 문자열은 전체 조회로 fallback")
    @WithMockLoginUser(id = 3L, email = "member3@admin.com")
    fun t6() {
        mvc.perform(get("$BASE_URL/trips/past-members").param("search", ""))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()").value(2))
    }
}
