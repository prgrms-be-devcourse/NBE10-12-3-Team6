package csh.back.domain.trip.chat.service

import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.trip.chat.dto.response.ChatMessagePageResponse
import csh.back.domain.trip.chat.dto.response.ChatMessageResponse
import csh.back.domain.trip.chat.dto.response.ChatReadStatusPageResponse
import csh.back.domain.trip.chat.dto.response.ChatReadStatusResponse
import csh.back.domain.trip.chat.entity.TripChatMessage
import csh.back.domain.trip.chat.entity.TripChatReadStatus
import csh.back.domain.trip.chat.enums.MessageType
import csh.back.domain.trip.chat.exception.InvalidChatContentException
import csh.back.domain.trip.chat.repository.TripChatMessageRepository
import csh.back.domain.trip.chat.repository.TripChatReadStatusRepository
import csh.back.domain.trip.group.repository.TripGroupRepository
import csh.back.domain.trip.member.repository.TripMemberRepository
import csh.back.domain.trip.member.validator.TripMemberValidator
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
@Transactional(readOnly = true)
class ChatService(
    private val tripChatMessageRepository: TripChatMessageRepository,
    private val tripChatReadStatusRepository: TripChatReadStatusRepository,
    private val tripGroupRepository: TripGroupRepository,
    private val memberRepository: MemberRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val tripMemberValidator: TripMemberValidator,
    private val messagingTemplate: SimpMessagingTemplate,
) {

    @Transactional
    fun sendMessage(tripGroupId: Long, senderId: Long, content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_CONTENT_LENGTH) {
            throw InvalidChatContentException("메시지는 1자 이상 ${MAX_CONTENT_LENGTH}자 이하여야 합니다.")
        }

        val tripGroup = tripGroupRepository.findById(tripGroupId).orElseThrow(::RuntimeException)
        val sender = memberRepository.findById(senderId).orElseThrow(::RuntimeException)

        val saved = tripChatMessageRepository.save(
            TripChatMessage(
                tripGroup = tripGroup,
                sender = sender,
                messageType = MessageType.TALK,
                content = trimmed,
            ),
        )

        broadcastAfterCommit(tripGroupId, ChatMessageResponse.from(saved, sender.name))
    }

    // SSE TripEvent 발행 지점에서 나란히 호출되는 시스템 메시지 기록 (5.7-1). TripEventService는 건드리지 않는다.
    @Transactional
    fun recordSystemMessage(tripGroupId: Long, content: String) {
        val tripGroup = tripGroupRepository.findById(tripGroupId).orElseThrow(::RuntimeException)

        val saved = tripChatMessageRepository.save(
            TripChatMessage(
                tripGroup = tripGroup,
                sender = null,
                messageType = MessageType.SYSTEM,
                content = content,
            ),
        )

        broadcastAfterCommit(tripGroupId, ChatMessageResponse.from(saved, senderName = null))
    }

    fun getHistory(tripGroupId: Long, memberId: Long, cursor: Long?, size: Int): ChatMessagePageResponse {
        tripMemberValidator.validMember(tripGroupId, memberId)
        val messages = tripChatMessageRepository.findLatestPage(tripGroupId, cursor, size + 1)
        return toPageResponse(messages, size)
    }

    fun getMessagesAfter(tripGroupId: Long, memberId: Long, cursor: Long, size: Int): ChatMessagePageResponse {
        tripMemberValidator.validMember(tripGroupId, memberId)
        val messages = tripChatMessageRepository.findAfter(tripGroupId, cursor, size + 1)
        return toPageResponse(messages, size)
    }

    @Transactional
    fun markAsRead(tripGroupId: Long, memberId: Long, lastReadMessageId: Long) {
        tripMemberValidator.validMember(tripGroupId, memberId)

        val readStatus = tripChatReadStatusRepository.findByTripGroupIdAndMemberId(tripGroupId, memberId)
            .orElseGet {
                val tripGroup = tripGroupRepository.findById(tripGroupId).orElseThrow(::RuntimeException)
                val member = memberRepository.findById(memberId).orElseThrow(::RuntimeException)
                tripChatReadStatusRepository.save(TripChatReadStatus(tripGroup = tripGroup, member = member))
            }
        val before = readStatus.lastReadMessageId
        readStatus.updateLastReadMessageId(lastReadMessageId)
        if (readStatus.lastReadMessageId > before) {
            broadcastReadStatusAfterCommit(tripGroupId, ChatReadStatusResponse(memberId, readStatus.lastReadMessageId))
        }
    }

    fun getUnreadCounts(memberId: Long): Map<Long, Long> {
        val tripGroupIds = tripGroupRepository.findAllByMemberId(memberId).mapNotNull { it.id }
        return tripChatMessageRepository.countUnreadByMember(memberId, tripGroupIds, UNREAD_COUNT_CAP)
    }

    fun getReadStatuses(tripGroupId: Long, memberId: Long): ChatReadStatusPageResponse {
        tripMemberValidator.validMember(tripGroupId, memberId)
        val totalMemberCount = tripMemberRepository.findByTripGroupId(tripGroupId).size
        val statuses = tripChatReadStatusRepository.findAllByTripGroupId(tripGroupId).map { ChatReadStatusResponse.from(it) }
        return ChatReadStatusPageResponse(totalMemberCount, statuses)
    }

    private fun toPageResponse(messages: List<TripChatMessage>, size: Int): ChatMessagePageResponse {
        val hasNext = messages.size > size
        val page = if (hasNext) messages.subList(0, size) else messages
        return ChatMessagePageResponse(
            messages = page.map { ChatMessageResponse.from(it, it.sender?.name) },
            hasNext = hasNext,
        )
    }

    private fun broadcastAfterCommit(tripGroupId: Long, response: ChatMessageResponse) {
        sendAfterCommit("/sub/trips/$tripGroupId/chat", response)
    }

    private fun broadcastReadStatusAfterCommit(tripGroupId: Long, response: ChatReadStatusResponse) {
        sendAfterCommit("/sub/trips/$tripGroupId/chat/read", response)
    }

    private fun sendAfterCommit(destination: String, payload: Any) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        messagingTemplate.convertAndSend(destination, payload)
                    }
                },
            )
            return
        }

        messagingTemplate.convertAndSend(destination, payload)
    }

    private companion object {
        const val MAX_CONTENT_LENGTH = 1000
        const val UNREAD_COUNT_CAP = 100
    }
}
