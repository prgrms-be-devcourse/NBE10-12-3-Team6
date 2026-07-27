package csh.back.domain.trip.group.support;

import csh.back.domain.member.dto.response.AuthFilterDto;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.List;

// 스프링 시큐리티 테스트가 제공하는 인터페이스로 테스트 시작 전에 SecurityContext를 만들어 주는 공장이라고 계약하는 단계
// implements 어떤 주문서를 받는 공장인지를 지정
public class WithMockLoginUserFactory implements WithSecurityContextFactory<WithMockLoginUser> {

	// 테스트 메서드에 @WithMockLoginUser가 붙어있으면, 해당 메서드 호출
	@Override
	public SecurityContext createSecurityContext(WithMockLoginUser annotation) {
		//JWT 필터가 principal에 넣는 타입
		AuthFilterDto loginUser = new AuthFilterDto(annotation.id(), annotation.email());

		var token = new UsernamePasswordAuthenticationToken(loginUser, null, List.of());

		SecurityContext context = SecurityContextHolder.createEmptyContext(); //정보를 담아주는 보관함 생성
		context.setAuthentication(token); //인증 객체를 담는 부분
		return context;
	}
}
