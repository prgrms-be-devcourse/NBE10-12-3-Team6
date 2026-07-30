package csh.back.domain.member.controller

import csh.back.domain.member.dto.request.LoginRequestDto
import csh.back.domain.member.dto.request.MemberRequestDto
import csh.back.domain.member.dto.response.LoginResponseDto
import csh.back.domain.member.dto.response.MemberResponseDto
import csh.back.domain.member.service.MemberService
import csh.back.domain.trip.member.service.TripMemberService
import csh.back.global.annotation.ApiV1
import csh.back.global.dto.ResponseData
import csh.back.global.jwt.CookieNames
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@ApiV1
@RestController
@RequestMapping("/auth")
@Tag(name = "회원 인증", description = "회원가입, 로그인, 로그아웃 관련 API")
class MemberController(
    private val memberService: MemberService,
    private val tripMemberService: TripMemberService,
) {
    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    fun signUp(@RequestBody @Valid request: MemberRequestDto): ResponseData<MemberResponseDto> =
        ResponseData(201, memberService.signUp(request.email!!, request.password!!, request.name!!))

    @Operation(summary = "로그인")
    @PostMapping("/login")
    fun login(
        @RequestBody @Valid request: LoginRequestDto,
        httpRequest: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseData<LoginResponseDto> {
        val userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT)
        val result = memberService.login(request.email, request.password, userAgent)

        // 초대 코드가 있으면 해당 여행에 멤버로 등록
        request.joinCode?.let { tripMemberService.createJoinMember(it, result.userInfo.id) }

        // accessToken: 30분, refreshToken: 7일 만료
        val accessCookie = ResponseCookie.from(CookieNames.ACCESS_TOKEN, result.accessToken)
            .httpOnly(true)
            .path("/")
            .maxAge(Duration.ofMinutes(30))
            .sameSite("Lax")
            .build()

        val refreshCookie = ResponseCookie.from(CookieNames.REFRESH_TOKEN, result.refreshToken)
            .httpOnly(true)
            .path("/")
            .maxAge(Duration.ofDays(7))
            .sameSite("Lax")
            .build()

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString())
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString())
        // 프론트 v1 호환을 위해 Authorization 헤더에도 토큰 전달 (refreshToken accessToken 순서)
        response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer ${result.refreshToken} ${result.accessToken}")

        return ResponseData(200, result.userInfo)
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    fun logout(
        @CookieValue(name = CookieNames.REFRESH_TOKEN, required = false) refreshToken: String?,
        response: HttpServletResponse,
    ): ResponseData<Void?> {
        refreshToken?.let { memberService.logout(it) }

        // 쿠키 만료 처리로 클라이언트 토큰 삭제
        response.addHeader(HttpHeaders.SET_COOKIE,
            ResponseCookie.from(CookieNames.ACCESS_TOKEN, "").path("/").maxAge(0).build().toString())
        response.addHeader(HttpHeaders.SET_COOKIE,
            ResponseCookie.from(CookieNames.REFRESH_TOKEN, "").path("/").maxAge(0).build().toString())

        return ResponseData(200, null)
    }
}