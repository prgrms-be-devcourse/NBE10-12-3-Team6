package csh.back.domain.trip.post.like.service

import csh.back.domain.trip.group.entity.TripGroup
import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.member.repository.TripMemberRepository
import csh.back.domain.trip.member.validator.TripMemberValidator
import csh.back.domain.trip.post.entity.Post
import csh.back.domain.trip.post.like.entity.PostLike
import csh.back.domain.trip.post.like.repository.PostLikeRepository
import csh.back.domain.trip.post.repository.PostRepository
import csh.back.domain.trip.timeline.entity.Timeline
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class PostLikeServiceTest {

    @Mock
    private lateinit var postLikeRepository: PostLikeRepository

    @Mock
    private lateinit var postRepository: PostRepository

    @Mock
    private lateinit var tripMemberRepository: TripMemberRepository

    @Mock
    private lateinit var tripMemberValidator: TripMemberValidator

    @InjectMocks
    private lateinit var postLikeService: PostLikeService

    private val tripGroupId = 1L
    private val postId = 10L
    private val memberId = 100L
    private val tripMemberId = 1000L

    private lateinit var tripMember: TripMember
    private lateinit var post: Post

    @BeforeEach
    fun setUp() {
        tripMember = org.mockito.Mockito.mock(TripMember::class.java)
        post = org.mockito.Mockito.mock(Post::class.java)

        doReturn(tripMemberId).`when`(tripMember).id
        doReturn(Optional.of(tripMember))
            .`when`(tripMemberRepository)
            .findByMemberIdAndTripGroupId(memberId, tripGroupId)

        doReturn(Optional.of(post))
            .`when`(postRepository)
            .findById(postId)

        setPostTripGroup(post, tripGroupId)
    }

    @Test
    @DisplayName("좋아요를 등록한다")
    fun like() {
        doReturn(false, true)
            .`when`(postLikeRepository)
            .existsByPostIdAndTripMemberId(postId, tripMemberId)

        doReturn(1L)
            .`when`(postLikeRepository)
            .countByPostId(postId)

        doReturn(org.mockito.Mockito.mock(PostLike::class.java))
            .`when`(postLikeRepository)
            .save(any(PostLike::class.java))

        val response = postLikeService.like(tripGroupId, postId, memberId)

        assertThat(response.postId).isEqualTo(postId)
        assertThat(response.liked).isTrue()
        assertThat(response.likeCount).isEqualTo(1L)

        verify(tripMemberValidator).validMember(tripGroupId, memberId)
        verify(postLikeRepository).save(any(PostLike::class.java))
    }

    @Test
    @DisplayName("이미 좋아요한 게시글은 중복 등록하지 않는다")
    fun duplicateLike() {
        doReturn(true)
            .`when`(postLikeRepository)
            .existsByPostIdAndTripMemberId(postId, tripMemberId)

        doReturn(1L)
            .`when`(postLikeRepository)
            .countByPostId(postId)

        val response = postLikeService.like(tripGroupId, postId, memberId)

        assertThat(response.postId).isEqualTo(postId)
        assertThat(response.liked).isTrue()
        assertThat(response.likeCount).isEqualTo(1L)

        verify(postLikeRepository, never()).save(any(PostLike::class.java))
    }

    @Test
    @DisplayName("좋아요를 취소한다")
    fun unlike() {
        doReturn(false)
            .`when`(postLikeRepository)
            .existsByPostIdAndTripMemberId(postId, tripMemberId)

        doReturn(0L)
            .`when`(postLikeRepository)
            .countByPostId(postId)

        val response = postLikeService.unlike(tripGroupId, postId, memberId)

        assertThat(response.postId).isEqualTo(postId)
        assertThat(response.liked).isFalse()
        assertThat(response.likeCount).isZero()

        verify(postLikeRepository)
            .deleteByPostIdAndTripMemberId(postId, tripMemberId)
    }

    @Test
    @DisplayName("현재 사용자의 좋아요 상태와 전체 좋아요 수를 조회한다")
    fun getStatus() {
        doReturn(true)
            .`when`(postLikeRepository)
            .existsByPostIdAndTripMemberId(postId, tripMemberId)

        doReturn(3L)
            .`when`(postLikeRepository)
            .countByPostId(postId)

        val response = postLikeService.getStatus(tripGroupId, postId, memberId)

        assertThat(response.postId).isEqualTo(postId)
        assertThat(response.liked).isTrue()
        assertThat(response.likeCount).isEqualTo(3L)
    }

    @Test
    @DisplayName("존재하지 않는 게시글에 좋아요를 등록할 수 없다")
    fun likePostNotFound() {
        doReturn(Optional.empty<Post>())
            .`when`(postRepository)
            .findById(postId)

        assertThatThrownBy {
            postLikeService.like(tripGroupId, postId, memberId)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("게시글이 존재하지 않습니다.")

        verify(postLikeRepository, never()).save(any(PostLike::class.java))
    }

    @Test
    @DisplayName("다른 여행 모임의 게시글에는 좋아요를 등록할 수 없다")
    fun likePostFromAnotherTripGroup() {
        setPostTripGroup(post, 999L)

        assertThatThrownBy {
            postLikeService.like(tripGroupId, postId, memberId)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("해당 여행의 게시글이 아닙니다.")

        verify(postLikeRepository, never()).save(any(PostLike::class.java))
    }

    @Test
    @DisplayName("여행 멤버가 존재하지 않으면 좋아요를 등록할 수 없다")
    fun tripMemberNotFound() {
        doReturn(Optional.empty<TripMember>())
            .`when`(tripMemberRepository)
            .findByMemberIdAndTripGroupId(memberId, tripGroupId)

        assertThatThrownBy {
            postLikeService.like(tripGroupId, postId, memberId)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("여행 멤버가 존재하지 않습니다.")

        verify(postLikeRepository, never()).save(any(PostLike::class.java))
    }

    private fun setPostTripGroup(post: Post, groupId: Long) {
        val timeline = org.mockito.Mockito.mock(Timeline::class.java)
        val tripGroup = org.mockito.Mockito.mock(TripGroup::class.java)

        doReturn(timeline).`when`(post).timeline
        doReturn(tripGroup).`when`(timeline).tripGroup
        doReturn(groupId).`when`(tripGroup).id
    }
}