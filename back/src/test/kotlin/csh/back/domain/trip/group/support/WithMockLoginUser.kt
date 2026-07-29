package csh.back.domain.trip.group.support

import org.springframework.security.test.context.support.WithSecurityContext
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockLoginUserFactory::class)
annotation class WithMockLoginUser(
    val id: Long = 1L,
    val email: String = "admin@admin.com"
)
