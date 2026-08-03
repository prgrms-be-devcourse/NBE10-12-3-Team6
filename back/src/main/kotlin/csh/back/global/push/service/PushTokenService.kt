package csh.back.global.push.service

import csh.back.domain.member.repository.MemberRepository
import csh.back.global.push.entity.DevicePlatform
import csh.back.global.push.entity.PushToken
import csh.back.global.push.repository.PushTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PushTokenService(
    private val pushTokenRepository:
    PushTokenRepository,
    private val memberRepository:
    MemberRepository
) {

    @Transactional
    fun register(
        memberId: Long,
        token: String,
        platform: DevicePlatform
    ) {
        val normalizedToken =
            token.trim()

        require(normalizedToken.isNotEmpty()) {
            "FCM 토큰은 비어 있을 수 없습니다."
        }

        val member =
            memberRepository
                .findById(memberId)
                .orElseThrow {
                    IllegalArgumentException(
                        "회원을 찾을 수 없습니다."
                    )
                }

        val existingToken =
            pushTokenRepository
                .findByToken(normalizedToken)

        if (existingToken != null) {
            existingToken
                .updateOwnerAndActivate(
                    member = member,
                    platform = platform
                )
            return
        }

        pushTokenRepository.save(
            PushToken.create(
                member = member,
                token = normalizedToken,
                platform = platform
            )
        )
    }

    @Transactional
    fun unregister(
        memberId: Long,
        token: String
    ) {
        val normalizedToken =
            token.trim()

        val pushToken =
            pushTokenRepository
                .findByTokenAndMemberId(
                    token = normalizedToken,
                    memberId = memberId
                )
                ?: return

        pushToken.deactivate()
    }

    fun getActiveTokens(
        memberId: Long
    ): List<PushToken> {
        return pushTokenRepository
            .findAllByMemberIdAndActiveTrue(
                memberId
            )
    }

    @Transactional
    fun deactivate(
        token: String
    ) {
        val normalizedToken =
            token.trim()

        pushTokenRepository
            .findByToken(normalizedToken)
            ?.deactivate()
    }
}