package csh.back.domain.trip.chat.interceptor

import csh.back.domain.member.dto.response.AuthFilterDto
import csh.back.domain.member.entity.Member
import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.member.repository.TripMemberRepository
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.Message
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

@ActiveProfiles("test")
@SpringBootTest
class StompChannelInterceptorTest {

    @Autowired lateinit var interceptor: StompChannelInterceptor

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var tripGroupRepository: TripGroupRepository

    @Autowired lateinit var tripMemberRepository: TripMemberRepository

    private lateinit var member: Member
    private lateinit var tripGroup: TripGroup

    @BeforeEach
    fun setUp() {
        member = memberRepository.save(Member("stomp-interceptor-${System.nanoTime()}@test.com", "pw", "멤버"))
        tripGroup = tripGroupRepository.save(
            TripGroup(
                owner = member,
                name = "인터셉터테스트여행",
                region = "제주",
                nights = 1,
                joinCode = "STOMP-ITC-${System.nanoTime()}",
                startDate = LocalDate.now().plusDays(1),
                endDate = LocalDate.now().plusDays(2),
            ),
        )
    }

    private fun stompMessage(command: StompCommand, destination: String?, principal: AuthFilterDto?): Message<ByteArray> {
        val accessor = StompHeaderAccessor.create(command)
        if (destination != null) accessor.destination = destination
        if (principal != null) accessor.user = UsernamePasswordAuthenticationToken(principal, null)
        return MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
    }

    @Test
    @DisplayName("트립 멤버가 아니면 SUBSCRIBE 시 예외가 발생한다")
    fun subscribeByNonMemberThrows() {
        val nonMember = memberRepository.save(Member("stomp-non-member-${System.nanoTime()}@test.com", "pw", "비멤버"))
        val message = stompMessage(
            StompCommand.SUBSCRIBE,
            "/sub/trips/${tripGroup.id}/chat",
            AuthFilterDto(nonMember.id!!, nonMember.email),
        )

        assertThatThrownBy { interceptor.preSend(message, MOCK_CHANNEL) }.isInstanceOf(RuntimeException::class.java)
    }

    @Test
    @DisplayName("트립 멤버는 SUBSCRIBE에 성공한다")
    fun subscribeByMemberSucceeds() {
        tripMemberRepository.save(TripMember(member = member, tripGroup = tripGroup, isAdmin = false))
        val message = stompMessage(
            StompCommand.SUBSCRIBE,
            "/sub/trips/${tripGroup.id}/chat",
            AuthFilterDto(member.id!!, member.email),
        )

        assertThatCode { interceptor.preSend(message, MOCK_CHANNEL) }.doesNotThrowAnyException()
    }

    @Test
    @DisplayName("트립 멤버가 아니면 SEND 시 예외가 발생한다")
    fun sendByNonMemberThrows() {
        val nonMember = memberRepository.save(Member("stomp-non-member-send-${System.nanoTime()}@test.com", "pw", "비멤버"))
        val message = stompMessage(
            StompCommand.SEND,
            "/pub/trips/${tripGroup.id}/chat",
            AuthFilterDto(nonMember.id!!, nonMember.email),
        )

        assertThatThrownBy { interceptor.preSend(message, MOCK_CHANNEL) }.isInstanceOf(RuntimeException::class.java)
    }

    @Test
    @DisplayName("채팅과 무관한 destination(예: /user/queue/errors)은 검증 없이 통과한다")
    fun unrelatedDestinationBypassesValidation() {
        val message = stompMessage(StompCommand.SUBSCRIBE, "/user/queue/errors", null)

        assertThatCode { interceptor.preSend(message, MOCK_CHANNEL) }.doesNotThrowAnyException()
    }

    @Test
    @DisplayName("짧은 시간 내 과도한 SEND는 rate limit 예외가 발생한다")
    fun exceedingRateLimitThrows() {
        tripMemberRepository.save(TripMember(member = member, tripGroup = tripGroup, isAdmin = false))
        val principal = AuthFilterDto(member.id!!, member.email)

        assertThatThrownBy {
            repeat(RATE_LIMIT_ATTEMPTS) {
                val message = stompMessage(StompCommand.SEND, "/pub/trips/${tripGroup.id}/chat", principal)
                interceptor.preSend(message, MOCK_CHANNEL)
            }
        }.isInstanceOf(RuntimeException::class.java)
    }

    private companion object {
        val MOCK_CHANNEL = org.mockito.Mockito.mock(org.springframework.messaging.MessageChannel::class.java)
        const val RATE_LIMIT_ATTEMPTS = 25
    }
}
