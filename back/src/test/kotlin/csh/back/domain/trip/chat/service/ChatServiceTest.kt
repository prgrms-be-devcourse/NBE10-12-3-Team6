package csh.back.domain.trip.chat.service

import csh.back.domain.member.entity.Member
import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.trip.chat.repository.TripChatReadStatusRepository
import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.member.repository.TripMemberRepository
import csh.back.domain.trip.chat.enums.MessageType
import csh.back.domain.trip.chat.exception.InvalidChatContentException
import csh.back.domain.trip.chat.repository.TripChatMessageRepository
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.repository.TripGroupRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.then
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDate

@ActiveProfiles("test")
@SpringBootTest
class ChatServiceTest {

    @Autowired lateinit var chatService: ChatService

    @Autowired lateinit var tripChatMessageRepository: TripChatMessageRepository

    @Autowired lateinit var tripGroupRepository: TripGroupRepository

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var tripMemberRepository: TripMemberRepository

    @Autowired lateinit var tripChatReadStatusRepository: TripChatReadStatusRepository

    @MockitoBean lateinit var messagingTemplate: SimpMessagingTemplate

    private lateinit var sender: Member
    private lateinit var tripGroup: TripGroup

    @BeforeEach
    fun setUp() {
        sender = memberRepository.save(Member("chat-sender-${System.nanoTime()}@test.com", "pw", "발신자"))
        tripGroup = tripGroupRepository.save(
            TripGroup(
                owner = sender,
                name = "채팅테스트여행",
                region = "서울",
                nights = 1,
                joinCode = "CHAT-SVC-${System.nanoTime()}",
                startDate = LocalDate.now().plusDays(1),
                endDate = LocalDate.now().plusDays(2),
            ),
        )
        tripMemberRepository.save(TripMember(member = sender, tripGroup = tripGroup, isAdmin = true))
    }

    @Test
    @DisplayName("정상 내용을 전송하면 TALK 메시지가 저장되고 트립 채널로 브로드캐스트된다")
    fun sendMessageSavesAndBroadcasts() {
        chatService.sendMessage(tripGroup.id!!, sender.id!!, "안녕하세요")

        val messages = tripChatMessageRepository.findAll().filter { it.tripGroup.id == tripGroup.id }
        assertThat(messages).hasSize(1)
        assertThat(messages[0].messageType).isEqualTo(MessageType.TALK)
        assertThat(messages[0].sender?.id).isEqualTo(sender.id)
        assertThat(messages[0].content).isEqualTo("안녕하세요")

        then(messagingTemplate).should()
            .convertAndSend(ArgumentMatchers.eq("/sub/trips/${tripGroup.id}/chat"), ArgumentMatchers.any(Any::class.java))
    }

    @Test
    @DisplayName("빈 내용을 전송하면 InvalidChatContentException이 발생하고 저장되지 않는다")
    fun sendBlankContentThrows() {
        assertThatThrownBy { chatService.sendMessage(tripGroup.id!!, sender.id!!, "   ") }
            .isInstanceOf(InvalidChatContentException::class.java)

        assertThat(tripChatMessageRepository.findAll().filter { it.tripGroup.id == tripGroup.id }).isEmpty()
        then(messagingTemplate).shouldHaveNoInteractions()
    }

    @Test
    @DisplayName("1000자를 초과하는 내용을 전송하면 InvalidChatContentException이 발생한다")
    fun sendTooLongContentThrows() {
        val tooLong = "a".repeat(1001)

        assertThatThrownBy { chatService.sendMessage(tripGroup.id!!, sender.id!!, tooLong) }
            .isInstanceOf(InvalidChatContentException::class.java)

        assertThat(tripChatMessageRepository.findAll().filter { it.tripGroup.id == tripGroup.id }).isEmpty()
    }

    @Test
    @DisplayName("recordSystemMessage는 SYSTEM 메시지를 sender 없이 저장하고 브로드캐스트한다")
    fun recordSystemMessageSavesAndBroadcasts() {
        chatService.recordSystemMessage(tripGroup.id!!, "장소가 삭제되었습니다.")

        val messages = tripChatMessageRepository.findAll().filter { it.tripGroup.id == tripGroup.id }
        assertThat(messages).hasSize(1)
        assertThat(messages[0].messageType).isEqualTo(MessageType.SYSTEM)
        assertThat(messages[0].sender).isNull()
        assertThat(messages[0].content).isEqualTo("장소가 삭제되었습니다.")

        then(messagingTemplate).should()
            .convertAndSend(ArgumentMatchers.eq("/sub/trips/${tripGroup.id}/chat"), ArgumentMatchers.any(Any::class.java))
    }

    @Test
    @DisplayName("getHistory는 cursor 없이 호출하면 최신 메시지부터 size개를 반환하고, 남은 메시지가 있으면 hasNext=true")
    fun getHistoryReturnsLatestPageWithHasNext() {
        repeat(5) { chatService.sendMessage(tripGroup.id!!, sender.id!!, "msg-$it") }

        val page = chatService.getHistory(tripGroup.id!!, sender.id!!, null, 3)

        assertThat(page.messages).hasSize(3)
        assertThat(page.hasNext).isTrue()
    }

    @Test
    @DisplayName("getHistory는 남은 메시지가 없으면 hasNext=false")
    fun getHistoryHasNextFalseWhenNoMoreMessages() {
        repeat(2) { chatService.sendMessage(tripGroup.id!!, sender.id!!, "msg-$it") }

        val page = chatService.getHistory(tripGroup.id!!, sender.id!!, null, 3)

        assertThat(page.messages).hasSize(2)
        assertThat(page.hasNext).isFalse()
    }

    @Test
    @DisplayName("getHistory는 여행 멤버가 아니면 예외가 발생한다")
    fun getHistoryThrowsWhenNotMember() {
        val outsider = memberRepository.save(Member("chat-outsider-${System.nanoTime()}@test.com", "pw", "외부인"))

        assertThatThrownBy { chatService.getHistory(tripGroup.id!!, outsider.id!!, null, 30) }
            .isInstanceOf(RuntimeException::class.java)
    }

    @Test
    @DisplayName("getMessagesAfter는 cursor보다 큰 메시지를 오름차순으로 반환한다")
    fun getMessagesAfterReturnsAscendingNewerMessages() {
        chatService.sendMessage(tripGroup.id!!, sender.id!!, "first")
        val cursor = tripChatMessageRepository.findAll().first { it.tripGroup.id == tripGroup.id }.id!!
        chatService.sendMessage(tripGroup.id!!, sender.id!!, "second")
        chatService.sendMessage(tripGroup.id!!, sender.id!!, "third")

        val page = chatService.getMessagesAfter(tripGroup.id!!, sender.id!!, cursor, 100)

        assertThat(page.messages).hasSize(2)
        assertThat(page.messages.map { it.content }).isEqualTo(listOf("second", "third"))
        assertThat(page.hasNext).isFalse()
    }

    @Test
    @DisplayName("markAsRead는 read_status가 없으면 새로 생성한다")
    fun markAsReadCreatesReadStatusWhenAbsent() {
        chatService.markAsRead(tripGroup.id!!, sender.id!!, 42L)

        val readStatus = tripChatReadStatusRepository.findByTripGroupIdAndMemberId(tripGroup.id!!, sender.id!!).orElseThrow()
        assertThat(readStatus.lastReadMessageId).isEqualTo(42L)
    }

    @Test
    @DisplayName("markAsRead는 기존 값보다 작은 id로는 역행하지 않는다")
    fun markAsReadDoesNotRegress() {
        chatService.markAsRead(tripGroup.id!!, sender.id!!, 100L)
        chatService.markAsRead(tripGroup.id!!, sender.id!!, 10L)

        val readStatus = tripChatReadStatusRepository.findByTripGroupIdAndMemberId(tripGroup.id!!, sender.id!!).orElseThrow()
        assertThat(readStatus.lastReadMessageId).isEqualTo(100L)
    }

    @Test
    @DisplayName("getUnreadCounts는 유저가 속한 모든 여행의 unread 개수를 반환한다")
    fun getUnreadCountsReturnsCountsForAllTrips() {
        repeat(3) { chatService.sendMessage(tripGroup.id!!, sender.id!!, "msg-$it") }

        val counts = chatService.getUnreadCounts(sender.id!!)

        assertThat(counts[tripGroup.id!!]).isEqualTo(3L)
    }
}
