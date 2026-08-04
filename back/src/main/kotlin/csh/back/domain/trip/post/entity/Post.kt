package csh.back.domain.trip.post.entity

import csh.back.domain.trip.member.entity.TripMember
import csh.back.domain.trip.timeline.entity.Timeline
import csh.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.Index
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(
    name = "trip_posts",
    indexes = [
        Index(
            name = "idx_trip_posts_author_created_id",
            columnList = "trip_member_id, created_at, id"
        )
    ]
)
class Post(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_member_id", nullable = false)
    var author: TripMember,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_timeline_id")
    var timeline: Timeline? = null,

    var type: String? = null,

    @Column(name = "original_filename", length = 255)
    var originalFilename: String? = null,

    var contentUrl: String? = null,
    var normalContentUrl: String? = null,
    var dataSaverContentUrl: String? = null,

    @Column(name = "dominant_color", length = 7)
    var dominantColor: String? = null,

    @Column(length = 1000)
    var content: String? = null
) : BaseEntity() {
    fun update(content: String?) {
        this.content = content
    }
}
