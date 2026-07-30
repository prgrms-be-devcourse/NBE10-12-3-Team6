package csh.back.domain.member.controller

import csh.back.global.mail.EmailCooldownGuard
import csh.back.global.mail.MailService
import csh.back.global.mail.exception.MailCooldownException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
/**
 * 진짜 로직은 그대로 흐르되, 외부 IO만 차단하는 방식이에요. 그래서 흐름/계약 검증에 딱 맞습니다.
 * smtp 및 redis 외부 시스템에 의존하고 있어서 Mockito를 이용해서 가짜 생성
 * flow 흐름 테스트로 유효하지 않는 메일 이용
 */
class EmailVerificationControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @MockitoBean
    lateinit var mailService: MailService

    @MockitoBean
    lateinit var emailCooldownGuard: EmailCooldownGuard

    @MockitoBean
    lateinit var redisTemplate: StringRedisTemplate

    // redisTemplate.opsForValue() 체인 호출을 mock하기 위한 중간 객체.
    // ValueOperations는 Spring Bean이 아니라 @MockitoBean으로 등록 불가하므로 수동 생성.
    private lateinit var valueOperations: ValueOperations<String, String>

    private val BASE_URL = "/api/v1/auth"
    private val newEmail = "newuser@test.com"
    private val newEmailKey = "email:verification:$newEmail"

    // 매 테스트마다 valueOperations를 새로 만들어 이전 테스트의 stub이 다음 테스트에 영향 주지 않도록 격리.
    // (@MockitoBean은 Spring이 자동 reset해주지만, 수동으로 만든 mock은 여기서 직접 초기화 필요)
    @BeforeEach
    fun setUp() {
        @Suppress("UNCHECKED_CAST")
        valueOperations = Mockito.mock(ValueOperations::class.java) as ValueOperations<String, String>
    }

    // Kotlin non-null 파라미터에 Mockito matcher(eq/any)를 그대로 넘기면 NPE 발생.
    // Java의 eq/any는 matcher를 내부 스택에 등록만 하고 null을 반환하는데,
    // Kotlin은 non-null 타입에 null이 오면 런타임 체크에서 실패한다.
    // 아래 헬퍼는 matcher를 등록한 뒤 non-null 더미 값을 반환해 타입 시스템을 만족시킴.
    private fun <T : Any> eqNN(value: T): T {
        ArgumentMatchers.eq(value)
        return value
    }

    private fun anyStringNN(): String {
        ArgumentMatchers.any(String::class.java)
        return ""
    }

    @Test
    @DisplayName("새 이메일이면 200, 인증 코드 발송 및 쿨다운 mark")
    fun t1() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations)

        mvc.perform(
            post("$BASE_URL/check_email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$newEmail"}""")
        )
            .andDo(print())
            .andExpect(handler().handlerType(MemberController::class.java))
            .andExpect(handler().methodName("checkEmail"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.statusCode").value(200))

        then(emailCooldownGuard).should().check("verification", newEmail)
        then(emailCooldownGuard).should().mark("verification", newEmail, 30L)
        then(mailService).should().sendHtmlEmail(
            eqNN(newEmail),
            eqNN("[Triplog] 이메일 인증 코드"),
            anyStringNN(),
        )
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 404, 발송/쿨다운 미실행")
    fun t2() {
        mvc.perform(
            post("$BASE_URL/check_email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"admin@admin.com"}""")
        )
            .andDo(print())
            .andExpect(handler().handlerType(MemberController::class.java))
            .andExpect(handler().methodName("checkEmail"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."))

        then(emailCooldownGuard).shouldHaveNoInteractions()
        then(mailService).shouldHaveNoInteractions()
    }

    @Test
    @DisplayName("이메일 형식이 잘못되면 400")
    fun t3() {
        mvc.perform(
            post("$BASE_URL/check_email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"invalid-email"}""")
        )
            .andDo(print())
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.statusCode").value(400))

        then(mailService).shouldHaveNoInteractions()
    }

    @Test
    @DisplayName("이메일 필드가 비어있으면 400")
    fun t4() {
        mvc.perform(
            post("$BASE_URL/check_email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":""}""")
        )
            .andDo(print())
            .andExpect(status().isBadRequest)

        then(mailService).shouldHaveNoInteractions()
    }

    @Test
    @DisplayName("쿨다운 중이면 429, 발송 미실행")
    fun t5() {
        willThrow(MailCooldownException("25초 후 재시도해 주세요."))
            .given(emailCooldownGuard).check("verification", newEmail)

        mvc.perform(
            post("$BASE_URL/check_email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$newEmail"}""")
        )
            .andDo(print())
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.statusCode").value(429))
            .andExpect(jsonPath("$.message").value("25초 후 재시도해 주세요."))

        then(mailService).shouldHaveNoInteractions()
        then(emailCooldownGuard).should(never()).mark(anyString(), anyString(), anyLong())
    }

    @Test
    @DisplayName("저장된 코드가 없으면 400, 삭제 미실행")
    fun t6() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations)
        given(valueOperations.get(newEmailKey)).willReturn(null)

        mvc.perform(
            post("$BASE_URL/verify_email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$newEmail","code":"123456"}""")
        )
            .andDo(print())
            .andExpect(handler().methodName("verifyEmail"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("인증 코드가 만료되었거나 존재하지 않습니다."))

        then(redisTemplate).should(never()).delete(anyString())
    }

    @Test
    @DisplayName("코드가 일치하지 않으면 400, 삭제 미실행")
    fun t7() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations)
        given(valueOperations.get(newEmailKey)).willReturn("111111")

        mvc.perform(
            post("$BASE_URL/verify_email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$newEmail","code":"999999"}""")
        )
            .andDo(print())
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("인증 코드가 일치하지 않습니다."))

        then(redisTemplate).should(never()).delete(anyString())
    }

    @Test
    @DisplayName("정상 인증 시 200, Redis에서 코드 삭제")
    fun t8() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations)
        given(valueOperations.get(newEmailKey)).willReturn("654321")

        mvc.perform(
            post("$BASE_URL/verify_email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$newEmail","code":"654321"}""")
        )
            .andDo(print())
            .andExpect(handler().methodName("verifyEmail"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.statusCode").value(200))

        then(redisTemplate).should().delete(newEmailKey)
    }

    @Test
    @DisplayName("code 필드가 비어있으면 400")
    fun t9() {
        mvc.perform(
            post("$BASE_URL/verify_email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$newEmail","code":""}""")
        )
            .andDo(print())
            .andExpect(status().isBadRequest)

        then(redisTemplate).shouldHaveNoInteractions()
    }
}
