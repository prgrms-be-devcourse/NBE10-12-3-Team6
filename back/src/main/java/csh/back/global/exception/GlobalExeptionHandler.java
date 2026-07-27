package csh.back.global.exception;

import csh.back.domain.member.exception.ExistingMemberException;
import csh.back.domain.trip.group.exception.NonMemberException;
import csh.back.domain.trip.group.exception.NotFoundException;
import csh.back.domain.trip.place.exception.DuplicateTripPlaceException;
import csh.back.global.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExeptionHandler {

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ErrorResponse> handleGroupNotFound(
			NotFoundException e
	) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse(404, e.getMessage()));  // 따옴표 제거 + new 추가
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		FieldError fieldError = e.getBindingResult().getFieldErrors().get(0);
		String message = String.format("%s: %s", fieldError.getField(), fieldError.getDefaultMessage());
		// 예시: "region: 공백일 수 없습니다"

		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse(400, message));
	}

	@ExceptionHandler(NonMemberException.class)
	public ResponseEntity<ErrorResponse> handleGroupNotFound(
			NonMemberException e
	) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(new ErrorResponse(403, e.getMessage()));  // 따옴표 제거 + new 추가
	}

	@ExceptionHandler(ExistingMemberException.class)
	public ResponseEntity<ErrorResponse> handleGroupNotFound(
			ExistingMemberException e
	) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse(404, e.getMessage()));  // 따옴표 제거 + new 추가
	}

	@ExceptionHandler(DuplicateTripPlaceException.class)
	public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateTripPlaceException e) {
		log.warn("Duplicate trip place Id: {}", e.getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse(409, "이미 등록된 장소입니다"));
	}
}
