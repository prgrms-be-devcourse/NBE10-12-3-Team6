package csh.back.domain.trip.post.repository

import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.post.entity.Post
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface PostRepository : JpaRepository<Post, Long> {
    fun findByTimelineTripGroupId(tripGroupId: Long): List<Post>
    fun findByAuthorId(authorId: Long): List<Post>
    fun findByAuthorIdInOrderByCreatedAtAsc(authorIds: List<Long>): List<Post>
    fun findAllByAuthorId(authorId: Long): List<Post>

    @Query(
        """
        SELECT p FROM Post p
        LEFT JOIN FETCH p.timeline t
        LEFT JOIN FETCH t.tripWishPlace
        WHERE p.author IN :members
        ORDER BY p.createdAt ASC
        """
    )
    fun findWithTimelineAndPlaceByAuthorIdIn(members: List<TripMember>): List<Post>

    fun findByAuthorIdAndCreatedAtBetween(
        tripMemberId: Long,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Post>
}
