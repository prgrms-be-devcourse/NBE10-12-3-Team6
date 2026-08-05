package csh.back.domain.member.service

import csh.back.domain.member.dto.response.LoginResponseDto
import csh.back.domain.member.dto.response.MemberResponseDto
import csh.back.domain.member.dto.web.LoginResult
import csh.back.domain.member.entity.Member
import csh.back.domain.member.entity.RefreshToken
import csh.back.domain.member.exception.ExistingMemberException
import csh.back.domain.member.exception.InvalidCredentialsException
import csh.back.domain.member.exception.InvalidPasswordException
import csh.back.domain.member.exception.KakaoMemberPasswordChangeException
import csh.back.domain.member.exception.LoginLockedException
import csh.back.domain.member.repository.MemberRepository
import csh.back.domain.presence.service.PresenceService
import csh.back.domain.member.repository.RefreshTokenRepository
import csh.back.global.jwt.JwtUtil
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MemberService(
    private val memberRepository: MemberRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil,
    private val presenceService: PresenceService,
    private val newDeviceLoginNotificationService: NewDeviceLoginNotificationService,
    private val loginAttemptTracker: LoginAttemptTracker,
    private val recoveryCodeGenerator: RecoveryCodeGenerator,
) {
    @Transactional
    fun signUp(email: String, password: String, name: String): MemberResponseDto {
        if (memberRepository.existsByEmail(email)) {
            throw ExistingMemberException("이미 사용 중인 이메일입니다.")
        }

        // recovery code 생성 → 응답에 raw 노출용으로 보관, 저장은 BCrypt 해시만
        // (이 시점 이후 서버는 원본 코드를 다시 재현할 수 없음 = 유저가 반드시 이번 응답을 저장해야 함)
        val rawRecoveryCode = recoveryCodeGenerator.generate()
        val recoveryCodeHash = passwordEncoder.encode(rawRecoveryCode)!!

        val member = memberRepository.save(
            Member(
                email = email,
                // Spring Framework 7.x에서 PasswordEncoder.encode()가 @Nullable로 선언돼 !! 필요
                password = passwordEncoder.encode(password)!!,
                name = name,
            ).apply { assignRecoveryCodeHash(recoveryCodeHash) }
        )

        return MemberResponseDto.fromSignup(member, rawRecoveryCode)
    }

    // RefreshToken row를 INSERT하므로 쓰기 트랜잭션 필요 (클래스 레벨 readOnly 오버라이드)
    @Transactional
    fun login(email: String, password: String, userAgent: String?, deviceId: String): LoginResult {
        // 이메일 미존재를 "회원 없음"으로 노출하면 계정 열거(enumeration)에 취약 —
        // InvalidCredentialsException으로 통합해 비밀번호 오류와 동일 메시지 반환
        val member: Member = memberRepository.findByEmail(email)
            .orElseThrow { InvalidCredentialsException() }

        // 락아웃 상태: 재시도 대기시간 안내
        if (member.isLocked()) {
            val remaining = Duration.between(LocalDateTime.now(), member.lockedUntil!!).seconds.coerceAtLeast(1)
            throw LoginLockedException(remaining)
        }

        if (!passwordEncoder.matches(password, member.password)) {
            // REQUIRES_NEW 트랜잭션 — 여기서 던지는 예외로 login()이 rollback되어도 카운트는 보존
            val newCount = loginAttemptTracker.recordFailure(member.id!!)

            // 이번 실패로 임계값 도달 → 다음 요청까지 기다리지 않고 즉시 락아웃 응답 (UX 개선)
            if (newCount != null && newCount >= LoginAttemptTracker.FAILURE_THRESHOLD) {
                throw LoginLockedException(LoginAttemptTracker.LOCK_DURATION.seconds)
            }

            // 아직 임계값 미만이면 401 + 남은 시도 힌트
            val remaining = newCount?.let { (LoginAttemptTracker.FAILURE_THRESHOLD - it).coerceAtLeast(0) }
            throw InvalidCredentialsException(remainingAttempts = remaining)
        }

        // 성공 시 카운트/락 리셋 — 같은 트랜잭션의 dirty checking으로 저장됨
        if (member.failedLoginCount > 0 || member.lockedUntil != null) {
            member.resetLoginFailures()
        }

        // id!!: JPA save 후 항상 id가 할당되므로 non-null 보장
        val accessToken = jwtUtil.generateAccessToken(member.id!!, member.email)
        // delete 이전에 호출 — delete 후에 호출하면 existsByMemberIdAndDeviceId가 항상 false를 반환해 판단 불가
        newDeviceLoginNotificationService.notifyIfNewDevice(member.id!!, member.email, deviceId, userAgent)
        // 같은 기기에서 재로그인 시 기존 토큰 교체 — (member_id, device_id) unique 제약 충족
        refreshTokenRepository.deleteByMemberIdAndDeviceId(member.id!!, deviceId)
        val refreshToken = refreshTokenRepository.save(RefreshToken(member = member, userAgent = userAgent, deviceId = deviceId))
        return LoginResult(LoginResponseDto.from(member), accessToken, refreshToken.token)
    }


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

    fun getLoginUser(memberId: Long): LoginResponseDto =
        memberRepository.findById(memberId)
            .map(LoginResponseDto::from)
            .orElseThrow { RuntimeException("존재하지 않는 회원입니다.") }

    // 기존 RefreshToken을 삭제하고 같은 (member, deviceId)로 새 토큰을 발급한다.
    // 동시 요청이 DELETE와 INSERT 사이에 끼어들어 unique 제약 위반이 발생하면,
    // 경쟁에서 이긴 요청이 이미 INSERT한 토큰을 반환해 강제 로그아웃을 방지한다.
    @Transactional
    fun rotateRefreshToken(oldRefreshToken: RefreshToken): RefreshToken {
        return try {
            refreshTokenRepository.deleteByMemberIdAndDeviceId(
                oldRefreshToken.member.id!!,
                oldRefreshToken.deviceId
            )
            refreshTokenRepository.save(
                RefreshToken(
                    member = oldRefreshToken.member,
                    deviceId = oldRefreshToken.deviceId,
                    userAgent = oldRefreshToken.userAgent,
                )
            )
        } catch (e: DataIntegrityViolationException) {
            refreshTokenRepository.findByMemberIdAndDeviceId(
                oldRefreshToken.member.id!!,
                oldRefreshToken.deviceId
            ).orElseThrow { e }
        }
    }

    @Transactional
    fun changePassword(memberId: Long, currentPassword: String, newPassword: String, deviceId: String) {
        val member = memberRepository.findById(memberId)
            .orElseThrow { RuntimeException("존재하지 않는 회원입니다.") }

        if (member.provider == "KAKAO") {
            throw KakaoMemberPasswordChangeException("카카오 로그인 회원은 비밀번호를 변경할 수 없습니다.")
        }

        if (!passwordEncoder.matches(currentPassword, member.password)) {
            throw InvalidPasswordException("현재 비밀번호가 일치하지 않습니다.")
        }

        member.updatePassword(passwordEncoder.encode(newPassword)!!)
        // 현재 기기는 세션 유지 — 다른 기기의 RefreshToken만 삭제
        refreshTokenRepository.deleteByMemberIdAndDeviceIdNot(memberId, deviceId)
    }

    @Transactional
    fun logout(refreshToken: String) {
       // deleteByToken 이후엔 member 참조가 사라지므로 먼저 memberId를 뽑아둔다
        val memberId = refreshTokenRepository.findByTokenWithMember(refreshToken)
            .map { it.member.id!! }
            .orElse(null)
        refreshTokenRepository.deleteByToken(refreshToken)

        // 로그아웃 시 열려있는 SSE 연결을 즉시 종료해 presence를 online 상태로 남기지 않음
        memberId?.let { presenceService.disconnectAll(it) }
    }
}
