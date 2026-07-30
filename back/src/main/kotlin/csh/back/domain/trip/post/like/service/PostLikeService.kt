package csh.back.domain.trip.post.like.service

import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.member.repository.TripMemberRepository
import csh.back.domain.trip.member.validator.TripMemberValidator
import csh.back.domain.trip.post.entity.Post
import csh.back.domain.trip.post.like.dto.response.PostLikeResponse
import csh.back.domain.trip.post.like.entity.PostLike
import csh.back.domain.trip.post.like.repository.PostLikeRepository
import csh.back.domain.trip.post.repository.PostRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PostLikeService(
    private val postLikeRepository: PostLikeRepository,
    private val postRepository: PostRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val tripMemberValidator: TripMemberValidator
) {

    @Transactional
    fun like(tripGroupId: Long, postId: Long, memberId: Long): PostLikeResponse {
        val tripMember = findTripMember(tripGroupId, memberId)
        val post = findPostInTripGroup(tripGroupId, postId)

        if (!postLikeRepository.existsByPostIdAndTripMemberId(postId, tripMember.id!!)) {
            postLikeRepository.save(PostLike.create(post, tripMember))
        }

        return response(postId, tripMember.id!!)
    }

    @Transactional
    fun unlike(tripGroupId: Long, postId: Long, memberId: Long): PostLikeResponse {
        val tripMember = findTripMember(tripGroupId, memberId)
        findPostInTripGroup(tripGroupId, postId)

        postLikeRepository.deleteByPostIdAndTripMemberId(postId, tripMember.id!!)

        return response(postId, tripMember.id!!)
    }

    fun getStatus(tripGroupId: Long, postId: Long, memberId: Long): PostLikeResponse {
        val tripMember = findTripMember(tripGroupId, memberId)
        findPostInTripGroup(tripGroupId, postId)
        return response(postId, tripMember.id!!)
    }

    private fun findTripMember(tripGroupId: Long, memberId: Long): TripMember {
        tripMemberValidator.validMember(tripGroupId, memberId)

        return tripMemberRepository.findByMemberIdAndTripGroupId(memberId, tripGroupId)
            .orElseThrow { IllegalArgumentException("여행 멤버가 존재하지 않습니다.") }
    }

    private fun findPostInTripGroup(tripGroupId: Long, postId: Long): Post {
        val post = postRepository.findById(postId)
            .orElseThrow { IllegalArgumentException("게시글이 존재하지 않습니다.") }

        val actualTripGroupId = post.timeline?.tripGroup?.id ?: post.author.tripGroup.id
        if (actualTripGroupId != tripGroupId) {
            throw IllegalArgumentException("해당 여행의 게시글이 아닙니다.")
        }

        return post
    }

    private fun response(postId: Long, tripMemberId: Long): PostLikeResponse =
        PostLikeResponse(
            postId = postId,
            liked = postLikeRepository.existsByPostIdAndTripMemberId(postId, tripMemberId),
            likeCount = postLikeRepository.countByPostId(postId)
        )
}
