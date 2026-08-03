package csh.back.domain.trip.post.like.repository

import csh.back.domain.trip.post.like.entity.PostLike
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PostLikeRepository : JpaRepository<PostLike, Long> {

    fun existsByPostIdAndTripMemberId(
        postId: Long,
        tripMemberId: Long
    ): Boolean

    fun countByPostId(
        postId: Long
    ): Long

    @Query(
        """
        select pl.post.id, count(pl)
        from PostLike pl
        where pl.post.id in :postIds
        group by pl.post.id
        """
    )
    fun countGroupByPostId(
        @Param("postIds")
        postIds: Collection<Long>
    ): List<Array<Any>>

    fun deleteByPostIdAndTripMemberId(
        postId: Long,
        tripMemberId: Long
    ): Long

    fun deleteAllByPostId(postId: Long): Long
}
