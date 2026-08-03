package csh.back.domain.trip.chat.repository

import csh.back.domain.trip.chat.entity.TripChatMessage

interface TripChatMessageRepositoryCustom {
    // cursor가 null이면 최신부터, 아니면 id < cursor를 id DESC로 size개 조회 (과거 방향 스크롤)
    fun findLatestPage(tripGroupId: Long, cursor: Long?, size: Int): List<TripChatMessage>

    // id > cursor를 id ASC로 size개 조회 (재접속 유실 복구용)
    fun findAfter(tripGroupId: Long, cursor: Long, size: Int): List<TripChatMessage>

    // tripGroupId별 unread 개수(각 cap으로 상한). read_status가 없는 방은 lastReadMessageId=0으로 취급
    fun countUnreadByMember(memberId: Long, tripGroupIds: List<Long>, cap: Int): Map<Long, Long>
}
