package csh.back.domain.member.service

import csh.back.domain.member.dto.response.LoginResponseDto
import csh.back.domain.member.dto.response.MemberResponseDto
import csh.back.domain.member.dto.web.LoginResult
import csh.back.domain.member.entity.Member
import csh.back.domain.member.exception.ExistingMemberException
import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.presence.service.PresenceService
import csh.back.global.jwt.JwtUtil
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MemberService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil,
    private val presenceService: PresenceService,
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

    fun login(email: String, password: String): LoginResult {
        val member: Member = memberRepository.findByEmail(email)
            .orElseThrow { RuntimeException("존재하지 않는 이메일입니다.") }

        if (!passwordEncoder.matches(password, member.password)) {
            throw RuntimeException("비밀번호가 일치하지 않습니다.")
        }

        // id!!: JPA save 후 항상 id가 할당되므로 non-null 보장
        val accessToken = jwtUtil.generateAccessToken(member.id!!, member.email)
        return LoginResult(LoginResponseDto.from(member), accessToken, member.refreshToken)
    }

    @Transactional
    fun logout(memberId: Long) {
        val member: Member = memberRepository.findById(memberId)
            .orElseThrow { RuntimeException("존재하지 않는 회원입니다.") }

        member.invalidateRefreshToken()

        // 로그아웃 시 열려있는 SSE 연결을 즉시 종료해 presence를 online 상태로 남기지 않음
        presenceService.disconnectAll(memberId)
    }
}