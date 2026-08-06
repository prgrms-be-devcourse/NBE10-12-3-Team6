package csh.back.domain.trip.chat.entity

import csh.back.domain.member.entity.Member
import csh.back.domain.trip.group.entity.TripGroup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TripChatReadStatusTest {

    private val member = Member("read-status-test@test.com", "pw", "테스터")
    private val tripGroup = TripGroup(
        owner = member,
        name = "읽음상태테스트여행",
        region = "부산",
        nights = 1,
        joinCode = "TRIP-CHAT-READ-STATUS-${System.nanoTime()}",
        startDate = LocalDate.now(),
        endDate = LocalDate.now().plusDays(1),
    )

    @Test
    @DisplayName("lastReadMessageId보다 큰 값으로 갱신하면 반영된다")
    fun updateWithGreaterIdUpdates() {
        val readStatus = TripChatReadStatus(tripGroup = tripGroup, member = member)

        readStatus.updateLastReadMessageId(10L)

        assertThat(readStatus.lastReadMessageId).isEqualTo(10L)
    }

    @Test
    @DisplayName("lastReadMessageId보다 작거나 같은 값으로는 역행하지 않는다")
    fun updateWithSmallerOrEqualIdDoesNotRegress() {
        val readStatus = TripChatReadStatus(tripGroup = tripGroup, member = member)
        readStatus.updateLastReadMessageId(10L)

        readStatus.updateLastReadMessageId(5L)
        readStatus.updateLastReadMessageId(10L)

        assertThat(readStatus.lastReadMessageId).isEqualTo(10L)
    }
}
