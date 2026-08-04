package csh.back.domain.trip.post.reminder.service

import csh.back.domain.member.entity.Member
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.post.entity.Post
import csh.back.domain.trip.post.reminder.entity.PostReminder
import csh.back.domain.trip.post.reminder.entity.ReminderStatus
import csh.back.domain.trip.post.reminder.repository.PostReminderRepository
import csh.back.domain.trip.post.repository.PostRepository
import csh.back.global.push.dto.PushMessage
import csh.back.global.push.entity.DevicePlatform
import csh.back.global.push.entity.PushToken
import csh.back.global.push.service.FirebasePushSender
import csh.back.global.push.service.PushTokenService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.lenient
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PostReminderSenderTest {

    @Mock lateinit var postRepository: PostRepository
    @Mock lateinit var postReminderRepository: PostReminderRepository
    @Mock lateinit var pushTokenService: PushTokenService
    @Mock lateinit var firebasePushSender: FirebasePushSender
    @InjectMocks lateinit var postReminderSender: PostReminderSender

    private lateinit var post: Post

    @BeforeEach
    fun setUp() {
        val author = org.mockito.Mockito.mock(TripMember::class.java)
        val member = org.mockito.Mockito.mock(Member::class.java)
        val tripGroup = org.mockito.Mockito.mock(TripGroup::class.java)
        post = org.mockito.Mockito.mock(Post::class.java)

        lenient().doReturn(author).`when`(post).author
        lenient().doReturn("제주도 여행\n첫째 날").`when`(post).content
        lenient().doReturn(member).`when`(author).member
        lenient().doReturn(tripGroup).`when`(author).tripGroup
        lenient().doReturn(MEMBER_ID).`when`(member).id
        lenient().doReturn(TRIP_GROUP_ID).`when`(tripGroup).id
    }

    @Test
    fun `이미 처리한 게시글은 다시 발송하지 않는다`() {
        doReturn(true).`when`(postReminderRepository).existsByPostId(POST_ID)

        postReminderSender.send(POST_ID)

        verifyNoInteractions(postRepository, pushTokenService, firebasePushSender)
    }

    @Test
    fun `리마인드 대상 게시글이 없으면 예외를 발생시킨다`() {
        doReturn(null).`when`(postRepository).findReminderPostById(POST_ID)

        assertThatThrownBy { postReminderSender.send(POST_ID) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("postId=$POST_ID")
    }

    @Test
    fun `활성 푸시 토큰이 없으면 실패 상태로 기록한다`() {
        preparePostAndReminder()
        doReturn(emptyList<PushToken>()).`when`(pushTokenService).getActiveTokens(MEMBER_ID)

        postReminderSender.send(POST_ID)

        val reminder = savedReminder()
        assertThat(reminder.status).isEqualTo(ReminderStatus.FAILED)
        assertThat(reminder.failureReason).contains("활성 FCM 토큰")
        verifyNoInteractions(firebasePushSender)
    }

    @Test
    fun `푸시 발송에 성공하면 메시지 ID와 성공 상태를 기록한다`() {
        val token = PushToken.create(
            org.mockito.Mockito.mock(Member::class.java),
            "fcm-token",
            DevicePlatform.ANDROID
        )
        val expectedMessage = PushMessage(
            title = "1년 전 여행을 기억하시나요?",
            body = "\"제주도 여행 첫째 날\" 여행 기록을 다시 확인해보세요.",
            targetUrl = "/trip/$TRIP_GROUP_ID/timeline?from=reminder&postId=$POST_ID",
            postId = POST_ID
        )
        preparePostAndReminder()
        doReturn(listOf(token)).`when`(pushTokenService).getActiveTokens(MEMBER_ID)
        doReturn("firebase-message-id")
            .`when`(firebasePushSender)
            .send("fcm-token", expectedMessage)

        postReminderSender.send(POST_ID)

        val reminder = savedReminder()
        assertThat(reminder.status).isEqualTo(ReminderStatus.SENT)
        assertThat(reminder.firebaseMessageId).isEqualTo("firebase-message-id")
        assertThat(reminder.sentAt).isNotNull()
    }

    private fun preparePostAndReminder() {
        doReturn(post).`when`(postRepository).findReminderPostById(POST_ID)
        doAnswer { invocation -> invocation.getArgument<PostReminder>(0) }
            .`when`(postReminderRepository)
            .save(any(PostReminder::class.java))
    }

    private fun savedReminder(): PostReminder {
        val captor = ArgumentCaptor.forClass(PostReminder::class.java)
        verify(postReminderRepository).save(captor.capture())
        return captor.value
    }

    companion object {
        private const val POST_ID = 10L
        private const val MEMBER_ID = 100L
        private const val TRIP_GROUP_ID = 1L
    }
}
