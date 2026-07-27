package csh.back.global.jwt;

import csh.back.domain.member.dto.response.AuthFilterDto;
import csh.back.domain.member.repository.MemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final MemberRepository memberRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1순위: 쿠키에서 토큰 조회
        String accessToken = getCookieValue(request, CookieNames.ACCESS_TOKEN);
        String refreshToken = getCookieValue(request, CookieNames.REFRESH_TOKEN);

        // 2순위: 쿠키에 없으면 Authorization 헤더에서 조회 (기존 v1 프론트 호환용)
        if (accessToken == null && refreshToken == null) {
            String[] tokensFromHeader = getTokensFromHeader(request);
            refreshToken = tokensFromHeader[0];
            accessToken = tokensFromHeader[1];
        }

        if (accessToken != null && jwtUtil.isValid(accessToken)) {
            // accessToken 유효 → 인증 처리
            setAuthentication(jwtUtil.getEmail(accessToken), jwtUtil.getMemberId(accessToken));
        } else if (refreshToken != null) {
            // accessToken 만료 또는 없음 → refreshToken으로 DB 조회 후 새 accessToken 발급
            memberRepository.findByRefreshToken(refreshToken).ifPresent(member -> {
                String newAccessToken = jwtUtil.generateAccessToken(member.getId(), member.getEmail());
                setAccessTokenCookie(response, newAccessToken);
                response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + member.getRefreshToken() + " " + newAccessToken);
                setAuthentication(member.getEmail(), member.getId());
            });
        }

        filterChain.doFilter(request, response);
    }

    // 요청 쿠키에서 원하는 이름의 값 꺼내기
    private String getCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (cookie.getName().equals(name)) {
                return cookie.getValue();
            }
        }
        return null;
    }

    // Authorization 헤더에서 "Bearer <refreshToken> <accessToken>" 파싱 (v1 방식 호환)
    // 반환: [0] = refreshToken, [1] = accessToken
    private String[] getTokensFromHeader(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return new String[]{null, null};
        }
        String[] parts = header.substring(7).split(" ");
        String refreshToken = parts.length > 0 ? parts[0] : null;
        String accessToken = parts.length > 1 ? parts[1] : null;
        return new String[]{refreshToken, accessToken};
    }

    // 갱신된 accessToken을 Set-Cookie로 내려주기
    private void setAccessTokenCookie(HttpServletResponse response, String accessToken) {
        ResponseCookie cookie = ResponseCookie.from(CookieNames.ACCESS_TOKEN, accessToken)
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofMinutes(30))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    // SecurityContextHolder에 인증 정보 등록 - principal: AuthFilterDto, details: memberId
    private void setAuthentication(String email, Long memberId) {
        AuthFilterDto principal = new AuthFilterDto(memberId, email);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        authentication.setDetails(memberId);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}