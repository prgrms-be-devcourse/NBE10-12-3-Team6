package csh.back.domain.member.service;

import csh.back.domain.member.dto.response.LoginResponseDto;
import csh.back.domain.member.dto.response.MemberResponseDto;
import csh.back.domain.member.dto.web.LoginResult;
import csh.back.domain.member.entity.Member;
import csh.back.domain.member.exception.ExistingMemberException;
import csh.back.domain.member.repository.MemberRepository;
import csh.back.global.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class MemberService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // 회원가입
    @Transactional
    public MemberResponseDto signUp(String email, String password, String name) {
        if (memberRepository.existsByEmail(email)) {
            throw new ExistingMemberException("이미 사용 중인 이메일입니다.");
        }

        Member newMember = Member.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .name(name)
                .build();

        Member member = memberRepository.save(newMember);

        return MemberResponseDto.from(member);
    }

    // 로그인
    public LoginResult login(String email, String password) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 이메일입니다."));

        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        String accessToken = jwtUtil.generateAccessToken(member.getId(), member.getEmail());

        return new LoginResult(LoginResponseDto.from(member), accessToken, member.getRefreshToken());
    }

    // 로그아웃 - refreshToken을 무효화하여 재로그인 없이는 accessToken을 재발급받지 못하게 함
    @Transactional
    public void logout(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 회원입니다."));

        member.invalidateRefreshToken();
    }
}