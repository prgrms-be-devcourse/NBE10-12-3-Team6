package csh.back.global.dto;

public record ErrorResponse(
		int statusCode,
		String message
) {
}
