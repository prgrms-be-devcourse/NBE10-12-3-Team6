package csh.back.domain.trip.chat.support

import csh.back.domain.member.dto.response.AuthFilterDto
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import java.security.Principal

// STOMP 세션의 Principal은 핸드셰이크 시점에 Security가 세팅한 Authentication 그대로 전파된다.
fun Principal?.toAuthFilterDto(): AuthFilterDto {
    val authentication = this as? UsernamePasswordAuthenticationToken
        ?: throw RuntimeException("인증 정보가 없습니다.")
    return authentication.principal as? AuthFilterDto
        ?: throw RuntimeException("인증 정보가 없습니다.")
}
