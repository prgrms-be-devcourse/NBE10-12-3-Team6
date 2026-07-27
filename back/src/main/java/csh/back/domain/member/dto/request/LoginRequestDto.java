package csh.back.domain.member.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// 로그인 요청 DTO
public record LoginRequestDto(
        @NotBlank @Email String email,
        @NotBlank String password,
		String joinCode
) {}