package csh.back.domain.trip.post.repository

import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.post.entity.Post
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.domain.Pageable
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
        ORDER BY p.createdAt ASC, p.id ASC
        """
    )
    fun findFirstPageWithTimelineAndPlaceByAuthorIn(
        @Param("members") members: List<TripMember>,
        pageable: Pageable
    ): List<Post>

    @Query(
        """
        SELECT p FROM Post p
        LEFT JOIN FETCH p.timeline t
        LEFT JOIN FETCH t.tripWishPlace
        WHERE p.author IN :members
          AND (
            p.createdAt > :cursorCreatedAt
            OR (p.createdAt = :cursorCreatedAt AND p.id > :cursorId)
          )
        ORDER BY p.createdAt ASC, p.id ASC
        """
    )
    fun findNextPageWithTimelineAndPlaceByAuthorIn(
        @Param("members") members: List<TripMember>,
        @Param("cursorCreatedAt") cursorCreatedAt: LocalDateTime,
        @Param("cursorId") cursorId: Long,
        pageable: Pageable
    ): List<Post>

    fun findByAuthorIdAndCreatedAtBetween(
        tripMemberId: Long,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<Post>
}
