package csh.back.global.push.entity

import csh.back.domain.member.entity.Member
import csh.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "push_tokens",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_push_token",
            columnNames = ["token"]
        )
    ]
)
class PushToken protected constructor() : BaseEntity() {

    @field:ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @field:JoinColumn(
        name = "member_id",
        nullable = false
    )
    lateinit var member: Member
        protected set

    @field:Column(
        name = "token",
        nullable = false,
        length = 1000
    )
    lateinit var token: String
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(
        name = "platform",
        nullable = false,
        length = 20
    )
    lateinit var platform: DevicePlatform
        protected set

    @field:Column(
        name = "active",
        nullable = false
    )
    var active: Boolean = true
        protected set

    private constructor(
        member: Member,
        token: String,
        platform: DevicePlatform
    ) : this() {
        this.member = member
        this.token = token
        this.platform = platform
        this.active = true
    }

    fun updateOwnerAndActivate(
        member: Member,
        platform: DevicePlatform
    ) {
        this.member = member
        this.platform = platform
        this.active = true
    }

    fun deactivate() {
        active = false
    }

    companion object {
        fun create(
            member: Member,
            token: String,
            platform: DevicePlatform
        ): PushToken {
            return PushToken(
                member = member,
                token = token,
                platform = platform
            )
        }
    }
}