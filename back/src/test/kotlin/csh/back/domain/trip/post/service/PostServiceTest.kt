package csh.back.domain.trip.post.service

import csh.back.domain.member.entity.Member
import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.group.service.TripGroupService
import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.member.repository.TripMemberRepository
import csh.back.domain.trip.member.validator.TripMemberValidator
import csh.back.domain.trip.post.dto.request.UpdatePostRequest
import csh.back.domain.trip.post.entity.Post
import csh.back.domain.trip.post.like.repository.PostLikeRepository
import csh.back.domain.trip.post.reminder.repository.PostReminderRepository
import csh.back.domain.trip.post.repository.PostRepository
import csh.back.domain.trip.timeline.repository.TimelineRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class PostServiceTest {

    @Mock lateinit var postRepository: PostRepository
    @Mock lateinit var tripMemberRepository: TripMemberRepository
    @Mock lateinit var timelineRepository: TimelineRepository
    @Mock lateinit var s3UploadService: S3UploadService
    @Mock lateinit var postImageProcessor: PostImageProcessor
    @Mock lateinit var tripMemberValidator: TripMemberValidator
    @Mock lateinit var tripGroupService: TripGroupService
    @Mock lateinit var postLikeRepository: PostLikeRepository
    @Mock lateinit var postReminderRepository: PostReminderRepository
    @Mock lateinit var eventPublisher: ApplicationEventPublisher

    @InjectMocks lateinit var postService: PostService

    @Test
    fun `20자를 초과한 내용은 저장하지 않는다`() {
        assertThatThrownBy {
            postService.create(TRIP_GROUP_ID, MEMBER_ID, null, null, "가".repeat(21))
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("20자 까지 입력이 가능합니다.")

        verifyNoInteractions(tripMemberValidator, postRepository)
    }

    @Test
    fun `이미지가 없으면 TEXT 게시글을 생성하고 내용을 정리한다`() {
        val author = org.mockito.Mockito.mock(TripMember::class.java)
        doReturn(Optional.of(author))
            .`when`(tripMemberRepository)
            .findByMemberIdAndTripGroupId(MEMBER_ID, TRIP_GROUP_ID)
        doAnswer { invocation -> invocation.getArgument<Post>(0) }
            .`when`(postRepository)
            .saveAndFlush(any(Post::class.java))

        val response = postService.create(
            TRIP_GROUP_ID,
            MEMBER_ID,
            null,
            null,
            "  여행 기록  "
        )

        val captor = ArgumentCaptor.forClass(Post::class.java)
        verify(postRepository).saveAndFlush(captor.capture())
        assertThat(captor.value.type).isEqualTo("TEXT")
        assertThat(captor.value.content).isEqualTo("여행 기록")
        assertThat(captor.value.contentUrl).isNull()
        assertThat(response.type).isEqualTo("TEXT")
        verifyNoInteractions(postImageProcessor, s3UploadService)
    }

    @Test
    fun `페이지 크기가 허용 범위를 벗어나면 조회하지 않는다`() {
        assertThatThrownBy {
            postService.getPosts(TRIP_GROUP_ID, MEMBER_ID, null, 11)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("최대 10개")

        verifyNoInteractions(tripMemberValidator, postRepository)
    }

    @Test
    fun `다른 작성자는 게시글을 수정할 수 없다`() {
        val post = org.mockito.Mockito.mock(Post::class.java)
        val author = org.mockito.Mockito.mock(TripMember::class.java)
        val authorMember = org.mockito.Mockito.mock(Member::class.java)
        val tripGroup = org.mockito.Mockito.mock(TripGroup::class.java)

        doReturn(Optional.of(post)).`when`(postRepository).findById(POST_ID)
        doReturn(null).`when`(post).timeline
        doReturn(author).`when`(post).author
        doReturn(tripGroup).`when`(author).tripGroup
        doReturn(TRIP_GROUP_ID).`when`(tripGroup).id
        doReturn(authorMember).`when`(author).member
        doReturn(OTHER_MEMBER_ID).`when`(authorMember).id

        assertThatThrownBy {
            postService.update(
                TRIP_GROUP_ID,
                POST_ID,
                MEMBER_ID,
                UpdatePostRequest("수정 내용")
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("작성자만 수정 및 삭제할 수 있습니다.")

        verify(post, never()).update(org.mockito.ArgumentMatchers.nullable(String::class.java))
    }

    companion object {
        private const val TRIP_GROUP_ID = 1L
        private const val POST_ID = 10L
        private const val MEMBER_ID = 100L
        private const val OTHER_MEMBER_ID = 200L
    }
}
