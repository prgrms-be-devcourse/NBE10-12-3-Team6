package csh.back.domain.member.support;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockMemberFactory.class)
public @interface WithMockMember {
    long id() default 1L;
    String email() default "admin@admin.com";
}