package csh.back.domain.trip.group.support

import csh.back.domain.member.dto.response.AuthFilterDto
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.support.WithSecurityContextFactory

class WithMockLoginUserFactory : WithSecurityContextFactory<WithMockLoginUser> {

    override fun createSecurityContext(annotation: WithMockLoginUser): SecurityContext {
        val loginUser = AuthFilterDto(annotation.id, annotation.email)

        val token = UsernamePasswordAuthenticationToken(loginUser, null, emptyList())

        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = token
        return context
    }
}
