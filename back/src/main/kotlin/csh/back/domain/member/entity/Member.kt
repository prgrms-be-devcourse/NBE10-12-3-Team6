package csh.back.domain.member.entity

import csh.back.global.entity.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "members")
class Member(
    val email: String,
    val password: String,
    val name: String,
) : BaseEntity()