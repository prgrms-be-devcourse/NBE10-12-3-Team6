package csh.back.domain.presence.controller

import csh.back.domain.trip.group.support.WithMockLoginUser
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * TestInitData:
 *   - admin(id=1) — tg1 함께: member3
 *   - member2(id=2) — tg2 함께: member3
 *   - member3(id=3) — tg1: admin, tg2: member2
 *
 * Presence 조회는 "요청자와 함께 여행한 적 있는 회원 + 자기 자신"만 응답에 포함.
 * Redis는 외부 시스템이므로 mock. 리포지토리(TripMemberRepository)는 실제 DB 사용.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PresenceV1ControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @MockitoBean
    lateinit var redisTemplate: StringRedisTemplate

    private lateinit var valueOperations: ValueOperations<String, String>

    private val BASE_URL = "/api/v1"

    @BeforeEach
    fun setUp() {
        @Suppress("UNCHECKED_CAST")
        valueOperations = Mockito.mock(ValueOperations::class.java) as ValueOperations<String, String>
        given(redisTemplate.opsForValue()).willReturn(valueOperations)
    }

    // Kotlin non-null + Mockito 매처 어댑터 (EmailVerificationControllerTest 참고)
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> anyNN(clazz: Class<T>): T {
        ArgumentMatchers.any(clazz)
        return Mockito.mock(clazz) as T
    }

    private fun anyListNN(): List<String> {
        ArgumentMatchers.anyList<String>()
        return emptyList()
    }

    @Test
    @DisplayName("presence status - admin이 member3(관련) + 999(무관) 조회 → member3만 응답에 포함")
    @WithMockLoginUser
    fun t1() {
        // member3(id=3)은 admin과 함께 여행한 적 있음 → 응답 포함
        // 999는 무관 → 응답 제외
        // 응답에 3만 남으니 MGET에 넘어가는 keys는 ["presence:3"] 1개
        given(valueOperations.multiGet(anyListNN())).willReturn(listOf("1"))

        mvc.perform(get("$BASE_URL/presence/status").param("userIds", "3", "999"))
            .andDo(print())
            .andExpect(handler().handlerType(PresenceV1Controller::class.java))
            .andExpect(handler().methodName("getStatus"))
            .andExpect(status().isOk)
            // 3은 online (multiGet가 "1" 반환)
            .andExpect(jsonPath("$.data.3").value(true))
            // 999는 관련 없어서 응답에서 제외
            .andExpect(jsonPath("$.data.999").doesNotExist())
    }

    @Test
    @DisplayName("presence status - Redis에 값 없으면 offline(false)")
    @WithMockLoginUser
    fun t2() {
        // multiGet에 null이 들어있으면 offline
        given(valueOperations.multiGet(anyListNN())).willReturn(listOf(null))

        mvc.perform(get("$BASE_URL/presence/status").param("userIds", "3"))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.3").value(false))
    }

    @Test
    @DisplayName("presence status - 관련 유저가 하나도 없으면 빈 응답 (Redis 조회 안 함)")
    @WithMockLoginUser
    fun t3() {
        // admin은 999, 998과 여행한 적 없음 → visibleIds 비어서 Redis 조회 스킵
        mvc.perform(get("$BASE_URL/presence/status").param("userIds", "999", "998"))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").isEmpty)
    }

    @Test
    @DisplayName("presence status - 자기 자신은 항상 조회 허용")
    @WithMockLoginUser
    fun t4() {
        // admin이 자기 자신(id=1) 조회 → 필터 통과 → Redis 조회
        given(valueOperations.multiGet(anyListNN())).willReturn(listOf("1"))

        mvc.perform(get("$BASE_URL/presence/status").param("userIds", "1"))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.1").value(true))
    }
}
