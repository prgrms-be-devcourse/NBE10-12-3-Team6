package csh.back.domain.member.repository

import csh.back.domain.member.entity.Member
import csh.back.domain.member.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    // member를 JOIN FETCH해서 필터에서 lazy loading 없이 안전하게 접근
    @Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.member WHERE rt.token = :token")
    fun findByTokenWithMember(token: String): Optional<RefreshToken>

    // derived delete는 SimpleJpaRepository의 @Transactional을 상속받지 않아 명시 필요
    @Transactional
    fun deleteByToken(token: String)

    // 같은 기기에서 재로그인 시 기존 토큰 교체 — bulk DELETE로 처리해 SELECT+DELETE 루프 방지
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.member.id = :memberId AND rt.deviceId = :deviceId")
    @Transactional
    fun deleteByMemberIdAndDeviceId(memberId: Long, deviceId: String)

    // 전체 기기 로그아웃용 — 현재 미사용, 향후 "모든 기기 로그아웃" API에서 호출
    @Transactional
    fun deleteAllByMember(member: Member)

    // 비밀번호 변경 시 현재 기기를 제외한 다른 기기 토큰만 삭제
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.member.id = :memberId AND rt.deviceId <> :deviceId")
    @Transactional
    fun deleteByMemberIdAndDeviceIdNot(memberId: Long, deviceId: String)

    // 새 기기 로그인 감지용 — 로그인 직전 해당 (member, device) 조합이 이미 알려진 기기인지 확인
    fun existsByMemberIdAndDeviceId(memberId: Long, deviceId: String): Boolean
}
