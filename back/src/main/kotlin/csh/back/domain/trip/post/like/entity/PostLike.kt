package csh.back.domain.trip.post.like.entity

import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.post.entity.Post
import csh.back.global.entity.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "post_likes",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_post_like_post_member",
            columnNames = ["post_id", "trip_member_id"]
        )
    ],
    indexes = [
        Index(name = "idx_post_like_post_id", columnList = "post_id"),
        Index(
            name = "idx_post_like_trip_member_id",
            columnList = "trip_member_id"
        )
    ]
)
class PostLike protected constructor() : BaseEntity() {

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "post_id", nullable = false)
    lateinit var post: Post
        protected set

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "trip_member_id", nullable = false)
    lateinit var tripMember: TripMember
        protected set

    private constructor(
        post: Post,
        tripMember: TripMember
    ) : this() {
        this.post = post
        this.tripMember = tripMember
    }

    companion object {
        fun create(
            post: Post,
            tripMember: TripMember
        ): PostLike =
            PostLike(
                post = post,
                tripMember = tripMember
            )
    }
}