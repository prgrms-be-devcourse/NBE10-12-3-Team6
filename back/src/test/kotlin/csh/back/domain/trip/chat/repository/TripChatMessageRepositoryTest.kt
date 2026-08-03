package csh.back.domain.trip.chat.repository

import csh.back.domain.member.entity.Member
import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.trip.chat.entity.TripChatMessage
import csh.back.domain.trip.chat.entity.TripChatReadStatus
import csh.back.domain.trip.chat.enums.MessageType
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.repository.TripGroupRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

@ActiveProfiles("test")
@SpringBootTest
class TripChatMessageRepositoryTest {

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var tripGroupRepository: TripGroupRepository

    @Autowired lateinit var tripChatMessageRepository: TripChatMessageRepository

    @Autowired lateinit var tripChatReadStatusRepository: TripChatReadStatusRepository

    private lateinit var member: Member
    private lateinit var tripGroup: TripGroup

    @BeforeEach
    fun setUp() {
        member = memberRepository.save(Member("chat-repo-test-${System.nanoTime()}@test.com", "pw", "테스터"))
        tripGroup = tripGroupRepository.save(
            TripGroup(
                owner = member,
                name = "채팅저장소테스트여행",
                region = "부산",
                nights = 1,
                joinCode = "TRIP-CHAT-REPO-${System.nanoTime()}",
                startDate = LocalDate.now(),
                endDate = LocalDate.now().plusDays(1),
            ),
        )
    }

    private fun saveMessage(content: String): TripChatMessage = tripChatMessageRepository.save(
        TripChatMessage(
            tripGroup = tripGroup,
            sender = member,
            messageType = MessageType.TALK,
            content = content,
        ),
    )

    @Test
    @DisplayName("cursor 없이 조회하면 최신 메시지부터 size개를 id 내림차순으로 반환한다")
    fun findLatestPageWithoutCursor() {
        val messages = (1..5).map { saveMessage("msg-$it") }

        val page = tripChatMessageRepository.findLatestPage(tripGroup.id!!, null, 3)

        assertThat(page).hasSize(3)
        assertThat(page.map { it.id }).isEqualTo(listOf(messages[4].id, messages[3].id, messages[2].id))
    }

    @Test
    @DisplayName("cursor를 지정하면 그보다 작은 id를 id 내림차순으로 반환한다 (과거 방향 스크롤)")
    fun findLatestPageWithCursor() {
        val messages = (1..5).map { saveMessage("msg-$it") }

        val page = tripChatMessageRepository.findLatestPage(tripGroup.id!!, messages[2].id, 10)

        assertThat(page.map { it.id }).isEqualTo(listOf(messages[1].id, messages[0].id))
    }

    @Test
    @DisplayName("findAfter는 cursor보다 큰 id를 id 오름차순으로 반환한다 (재접속 유실 복구용)")
    fun findAfterReturnsAscendingNewerMessages() {
        val messages = (1..5).map { saveMessage("msg-$it") }

        val recovered = tripChatMessageRepository.findAfter(tripGroup.id!!, messages[1].id!!, 10)

        assertThat(recovered.map { it.id }).isEqualTo(listOf(messages[2].id, messages[3].id, messages[4].id))
    }

    @Test
    @DisplayName("read_status가 없는 방은 전체 메시지 수를 unread로 센다")
    fun countUnreadWithoutReadStatusCountsAll() {
        repeat(3) { saveMessage("msg-$it") }

        val result = tripChatMessageRepository.countUnreadByMember(member.id!!, listOf(tripGroup.id!!), 100)

        assertThat(result[tripGroup.id!!]).isEqualTo(3L)
    }

    @Test
    @DisplayName("read_status 이후의 메시지만 unread로 센다")
    fun countUnreadCountsOnlyAfterLastRead() {
        val messages = (1..5).map { saveMessage("msg-$it") }
        tripChatReadStatusRepository.save(
            TripChatReadStatus(tripGroup = tripGroup, member = member).apply {
                updateLastReadMessageId(messages[1].id!!)
            },
        )

        val result = tripChatMessageRepository.countUnreadByMember(member.id!!, listOf(tripGroup.id!!), 100)

        assertThat(result[tripGroup.id!!]).isEqualTo(3L)
    }

    @Test
    @DisplayName("unread 개수는 cap을 넘지 않는다")
    fun countUnreadIsCappedAtLimit() {
        repeat(5) { saveMessage("msg-$it") }

        val result = tripChatMessageRepository.countUnreadByMember(member.id!!, listOf(tripGroup.id!!), 2)

        assertThat(result[tripGroup.id!!]).isEqualTo(2L)
    }
}
