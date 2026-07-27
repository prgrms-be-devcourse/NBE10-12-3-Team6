package csh.back.domain.trip.group.support;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockLoginUserFactory.class)
public @interface WithMockLoginUser {
	long id() default 1L;
	String email() default "admin@admin.com";
}
