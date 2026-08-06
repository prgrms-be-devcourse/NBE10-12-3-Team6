package csh.back.domain.trip.event.service

import csh.back.domain.trip.event.enums.TripEventType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class TripEventServiceTest {

    @Test
    @DisplayName("자신이 발생시킨 이벤트는 자신에게 전달하지 않는다")
    fun doesNotDeliverOwnEvent() {
        assertFalse(
            shouldDeliverTripEvent(
                subscriberMemberId = 1L,
                actorMemberId = 1L,
            ),
        )
    }

    @Test
    @DisplayName("다른 멤버가 발생시킨 이벤트는 전달한다")
    fun deliversOtherMembersEvent() {
        assertTrue(
            shouldDeliverTripEvent(
                subscriberMemberId = 1L,
                actorMemberId = 2L,
            ),
        )
    }

    @Test
    @DisplayName("시스템 이벤트는 모든 멤버에게 전달한다")
    fun deliversSystemEvent() {
        assertTrue(
            shouldDeliverTripEvent(
                subscriberMemberId = 1L,
                actorMemberId = null,
            ),
        )
    }

    @Test
    @DisplayName("타임라인에 영향을 주는 이벤트를 구분한다")
    fun distinguishesTimelineEvents() {
        assertTrue(TripEventType.TIMELINE_PLACE_CONFIRMED.affectsTimeline())
        assertTrue(TripEventType.VOTE_CREATED.affectsTimeline())
        assertFalse(TripEventType.WISH_PLACE_ADDED.affectsTimeline())
    }
}
