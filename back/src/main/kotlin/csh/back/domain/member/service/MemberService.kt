package csh.back.domain.member.service

import csh.back.domain.member.dto.response.LoginResponseDto
import csh.back.domain.member.dto.response.MemberResponseDto
import csh.back.domain.member.dto.web.LoginResult
import csh.back.domain.member.entity.Member
import csh.back.domain.member.entity.RefreshToken
import csh.back.domain.member.exception.ExistingMemberException
import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.member.repository.RefreshTokenRepository
import csh.back.global.jwt.JwtUtil
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MemberService(
    private val memberRepository: MemberRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil,
) {
    @Transactional
    fun signUp(email: String, password: String, name: String): MemberResponseDto {
        if (memberRepository.existsByEmail(email)) {
            throw ExistingMemberException("이미 사용 중인 이메일입니다.")
        }

        val member = memberRepository.save(
            Member(
                email = email,
                // Spring Framework 7.x에서 PasswordEncoder.encode()가 @Nullable로 선언돼 !! 필요
                password = passwordEncoder.encode(password)!!,
                name = name,
            )
        )

        return MemberResponseDto.from(member)
    }

    // RefreshToken row를 INSERT하므로 쓰기 트랜잭션 필요 (클래스 레벨 readOnly 오버라이드)
    @Transactional
    fun login(email: String, password: String, userAgent: String?, deviceId: String): LoginResult {
        val member: Member = memberRepository.findByEmail(email)
            .orElseThrow { RuntimeException("존재하지 않는 이메일입니다.") }

        if (!passwordEncoder.matches(password, member.password)) {
            throw RuntimeException("비밀번호가 일치하지 않습니다.")
        }

        // id!!: JPA save 후 항상 id가 할당되므로 non-null 보장
        val accessToken = jwtUtil.generateAccessToken(member.id!!, member.email)
        // 같은 기기에서 재로그인 시 기존 토큰 교체 — (member_id, device_id) unique 제약 충족
        refreshTokenRepository.deleteByMemberIdAndDeviceId(member.id!!, deviceId)
        val refreshToken = refreshTokenRepository.save(RefreshToken(member = member, userAgent = userAgent, deviceId = deviceId))
        return LoginResult(LoginResponseDto.from(member), accessToken, refreshToken.token)
    }

    @Transactional
    fun findOrCreateKakaoMember(kakaoId: String, nickname: String): Member {
        return memberRepository.findByProviderAndProviderId("KAKAO", kakaoId)
            .orElseGet {
                memberRepository.save(
                    Member(
                        // 카카오는 비즈 앱 심사 없이 이메일 제공 불가 → unique 제약 충족용 placeholder
                        email = "kakao_${kakaoId}@triplog.local",
                        // 카카오 사용자는 비밀번호 인증을 사용하지 않음 → 랜덤 UUID로 채움
                        password = passwordEncoder.encode(UUID.randomUUID().toString())!!,
                        name = nickname,
                        provider = "KAKAO",
                        providerId = kakaoId,
                    )
                )
            }
    }

    @Transactional
    fun logout(refreshToken: String) {
        refreshTokenRepository.deleteByToken(refreshToken)
    }
}