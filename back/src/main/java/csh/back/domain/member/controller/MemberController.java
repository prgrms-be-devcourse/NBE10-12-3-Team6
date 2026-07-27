package csh.back.domain.member.controller;

import csh.back.domain.member.dto.request.LoginRequestDto;
import csh.back.domain.member.dto.request.MemberRequestDto;
import csh.back.domain.member.dto.response.LoginResponseDto;
import csh.back.domain.member.dto.response.MemberResponseDto;
import csh.back.domain.member.dto.web.LoginResult;
import csh.back.domain.member.service.MemberService;
import csh.back.domain.trip.member.service.TripMemberService;
import csh.back.global.annotation.ApiV1;
import csh.back.global.dto.ResponseData;
import csh.back.global.jwt.CookieNames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

// 회원 관련 요청을 처리하는 컨트롤러 (회원가입, 로그인, 로그아웃)
// 인증 방식: 쿠키(v2) + Authorization 헤더(v1) 하이브리드 지원 - 프론트 전환 기간 동안 둘 다 발급
@ApiV1
@RequiredArgsConstructor
@RequestMapping("/auth")
@RestController
@Tag(name = "회원 인증", description = "회원가입, 로그인, 로그아웃 관련 API")
public class MemberController {
    private final MemberService memberService;
    private final TripMemberService tripMemberService;

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    public ResponseData<MemberResponseDto> signUp(
            @RequestBody @Valid MemberRequestDto request) {
        return new ResponseData<>(201, memberService.signUp(request.email(), request.password(), request.name()));
    }

    /**
     * 로그인 요청 처리
     * - accessToken(30분), refreshToken(7일)을 httpOnly 쿠키(Set-Cookie)로 전달
     * - 기존 v1 프론트 호환을 위해 Authorization 헤더로도 동일한 토큰을 함께 전달
     * - joinCode가 있으면 로그인과 동시에 해당 여행 그룹에 참여 처리
     */
    @Operation(summary = "로그인")
    @PostMapping("/login")
    public ResponseData<LoginResponseDto> login(
            @RequestBody @Valid LoginRequestDto request,
            HttpServletResponse response) {

        LoginResult result = memberService.login(request.email(), request.password());

        if (request.joinCode() != null) {
            tripMemberService.createJoinMember(request.joinCode(), result.userInfo().id());
        }

        // accessToken 쿠키 - 30분 후 자연 만료 (JwtAuthenticationFilter가 자동 재발급)
        ResponseCookie accessCookie = ResponseCookie.from(CookieNames.ACCESS_TOKEN, result.accessToken())
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofMinutes(30))
                .sameSite("Lax")
                .build();

        // refreshToken 쿠키 - 7일간 유지 (DB의 refreshToken 값 자체는 로그아웃 전까지 계속 유효)
        ResponseCookie refreshCookie = ResponseCookie.from(CookieNames.REFRESH_TOKEN, result.refreshToken())
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofDays(7))
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        // 기존 v1 프론트 호환용 - Authorization 헤더로도 동일하게 전달
        response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + result.refreshToken() + " " + result.accessToken());

        return new ResponseData<>(200, result.userInfo());
    }

    /**
     * 로그아웃 요청 처리
     * - DB에 저장된 refreshToken을 새 값으로 교체하여 기존 값을 무효화
     *   (기존 accessToken은 즉시 무효화되지 않고 최대 30분 후 자연 만료됨)
     * - accessToken/refreshToken 쿠키를 즉시 만료시켜 브라우저에서 삭제
     * - 인증되지 않은 요청은 SecurityConfig(anyRequest().authenticated())에서
     *   컨트롤러 진입 전에 이미 걸러지므로, 아래 null 체크는 방어 코드 성격
     */
    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public ResponseData<Void> logout(HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getDetails() == null) {
            throw new RuntimeException("로그인이 필요합니다.");
        }

        Long memberId = (Long) authentication.getDetails();
        memberService.logout(memberId);

        response.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from(CookieNames.ACCESS_TOKEN, "").path("/").maxAge(0).build().toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from(CookieNames.REFRESH_TOKEN, "").path("/").maxAge(0).build().toString());

        return new ResponseData<>(200, null);
    }
}