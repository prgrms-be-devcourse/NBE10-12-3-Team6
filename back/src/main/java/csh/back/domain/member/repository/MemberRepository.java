package csh.back.domain.member.repository;

import csh.back.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

// 회원 레포지토리
public interface MemberRepository extends JpaRepository<Member, Long> {
    // 이메일 중복 체크
    boolean existsByEmail(String email);

    // 이메일로 회원 조회
    Optional<Member> findByEmail(String email);

    // Refresh Token으로 회원 조회
    Optional<Member> findByRefreshToken(String refreshToken);
}