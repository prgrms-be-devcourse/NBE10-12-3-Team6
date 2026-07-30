package csh.back.domain.trip.group.settings.entity

import csh.back.domain.trip.group.entity.TripGroup
import csh.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnDefault

@Entity
@Table(name = "trip_group_settings")
class TripGroupSettings(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_group_id", nullable = false, unique = true)
    val tripGroup: TripGroup,

    @ColumnDefault("true")
    @Column(nullable = false)
    var isAnonymousVote: Boolean = true,

    @ColumnDefault("60")
    @Column(nullable = false)
    var freeTimeMinutes: Int = 60,
) : BaseEntity()