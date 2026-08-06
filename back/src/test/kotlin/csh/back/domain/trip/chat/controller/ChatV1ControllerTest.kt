package csh.back.domain.trip.chat.controller

import csh.back.domain.member.entity.Member
import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.member.support.WithMockMember
import csh.back.domain.trip.chat.entity.TripChatMessage
import csh.back.domain.trip.chat.enums.MessageType
import csh.back.domain.trip.chat.repository.TripChatMessageRepository
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.member.repository.TripMemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatV1ControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var tripGroupRepository: TripGroupRepository

    @Autowired
    lateinit var tripMemberRepository: TripMemberRepository

    @Autowired
    lateinit var tripChatMessageRepository: TripChatMessageRepository

    private lateinit var tripGroup: TripGroup

    @BeforeEach
    fun setUp() {
        val owner = memberRepository.findById(1L).orElseThrow()
        tripGroup = tripGroupRepository.save(
            TripGroup(
                owner = owner,
                name = "채팅 REST 테스트 여행",
                region = "제주",
                nights = 1,
                joinCode = UUID.randomUUID().toString(),
                startDate = LocalDate.now().plusDays(1),
                endDate = LocalDate.now().plusDays(2),
            ),
        )
        tripMemberRepository.save(TripMember(member = owner, tripGroup = tripGroup, isAdmin = true))
    }

    private fun saveMessage(sender: Member, content: String): TripChatMessage =
        tripChatMessageRepository.save(
            TripChatMessage(tripGroup = tripGroup, sender = sender, messageType = MessageType.TALK, content = content),
        )

    @Test
    @DisplayName("채팅 히스토리를 cursor 없이 조회하면 최신 메시지부터 반환한다")
    @WithMockMember(id = 1L, email = "admin@admin.com")
    fun findMessagesReturnsLatestPage() {
        val owner = memberRepository.findById(1L).orElseThrow()
        repeat(3) { saveMessage(owner, "msg-$it") }

        mvc.perform(get("$BASE_URL/trips/${tripGroup.id}/chat/messages").param("size", "2"))
            .andExpect(handler().handlerType(ChatV1Controller::class.java))
            .andExpect(handler().methodName("findMessages"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.messages.length()").value(2))
            .andExpect(jsonPath("$.data.hasNext").value(true))
            .andExpect(jsonPath("$.data.messages[0].content").value("msg-2"))
    }

    @Test
    @DisplayName("cursor 이후의 유실 메시지를 오름차순으로 복구 조회한다")
    @WithMockMember(id = 1L, email = "admin@admin.com")
    fun findMessagesAfterReturnsMissedMessages() {
        val owner = memberRepository.findById(1L).orElseThrow()
        val first = saveMessage(owner, "first")
        saveMessage(owner, "second")

        mvc.perform(
            get("$BASE_URL/trips/${tripGroup.id}/chat/messages/after")
                .param("cursor", first.id.toString())
                .param("size", "100"),
        )
            .andExpect(handler().methodName("findMessagesAfter"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.messages.length()").value(1))
            .andExpect(jsonPath("$.data.messages[0].content").value("second"))
    }

    @Test
    @DisplayName("읽음 처리 요청을 보내면 200을 응답하고 이후 unread-counts에서 반영된다")
    @WithMockMember(id = 1L, email = "admin@admin.com")
    fun markAsReadUpdatesUnreadCounts() {
        val owner = memberRepository.findById(1L).orElseThrow()
        val message = saveMessage(owner, "읽음 대상 메시지")

        mvc.perform(
            put("$BASE_URL/trips/${tripGroup.id}/chat/read")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"lastReadMessageId": ${message.id}}"""),
        )
            .andExpect(handler().methodName("markAsRead"))
            .andExpect(status().isOk)

        mvc.perform(get("$BASE_URL/trips/chat/unread-counts"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data['${tripGroup.id}']").value(0))
    }

    @Test
    @DisplayName("안 읽은 메시지 수 조회 시 유저가 속한 모든 여행의 unread count를 반환한다")
    @WithMockMember(id = 1L, email = "admin@admin.com")
    fun findUnreadCountsReturnsCountPerTrip() {
        val owner = memberRepository.findById(1L).orElseThrow()
        repeat(4) { saveMessage(owner, "msg-$it") }

        val result = mvc.perform(get("$BASE_URL/trips/chat/unread-counts"))
            .andExpect(handler().methodName("findUnreadCounts"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data['${tripGroup.id}']").value(4))
            .andReturn()

        assertThat(result.response.contentAsString).contains(tripGroup.id.toString())
    }

    @Test
    @DisplayName("읽음 상태 조회 시 읽음 처리한 멤버의 lastReadMessageId가 반환된다")
    @WithMockMember(id = 1L, email = "admin@admin.com")
    fun findReadStatusesReturnsMemberStatuses() {
        val owner = memberRepository.findById(1L).orElseThrow()
        val message = saveMessage(owner, "읽음 상태 대상 메시지")

        mvc.perform(
            put("$BASE_URL/trips/${tripGroup.id}/chat/read")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"lastReadMessageId": ${message.id}}"""),
        ).andExpect(status().isOk)

        mvc.perform(get("$BASE_URL/trips/${tripGroup.id}/chat/read-statuses"))
            .andExpect(handler().methodName("findReadStatuses"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalMemberCount").value(1))
            .andExpect(jsonPath("$.data.statuses[0].memberId").value(owner.id))
            .andExpect(jsonPath("$.data.statuses[0].lastReadMessageId").value(message.id))
    }

    private companion object {
        const val BASE_URL = "/api/v1"
    }
}
