package csh.back.global.push.repository

import csh.back.global.push.entity.PushToken
import org.springframework.data.jpa.repository.JpaRepository

interface PushTokenRepository :
    JpaRepository<PushToken, Long> {

    fun findByToken(
        token: String
    ): PushToken?

    fun findByTokenAndMemberId(
        token: String,
        memberId: Long
    ): PushToken?

    fun findAllByMemberIdAndActiveTrue(
        memberId: Long
    ): List<PushToken>
}