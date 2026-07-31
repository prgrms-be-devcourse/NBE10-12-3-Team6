package csh.back.global.exception

import csh.back.domain.member.exception.EmailVerificationException
import csh.back.domain.member.exception.ExistingMemberException
import csh.back.domain.trip.group.exception.NonMemberException
import csh.back.domain.trip.group.exception.NotFoundException
import csh.back.domain.trip.group.settings.exception.InvalidFreeTimeMinutesException
import csh.back.domain.trip.group.settings.exception.TripGroupSettingsLockedException
import csh.back.domain.trip.place.exception.DuplicateTripPlaceException
import csh.back.global.dto.ErrorResponse
import csh.back.global.mail.exception.MailCooldownException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExeptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(NotFoundException::class)
    fun handleGroupNotFound(e: NotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(404, e.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val fieldError = e.bindingResult.fieldErrors[0]
        val message = "${fieldError.field}: ${fieldError.defaultMessage}"
        // 예시: "region: 공백일 수 없습니다"

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(400, message))
    }

    @ExceptionHandler(NonMemberException::class)
    fun handleGroupNotFound(e: NonMemberException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(403, e.message))
    }

    @ExceptionHandler(ExistingMemberException::class)
    fun handleGroupNotFound(e: ExistingMemberException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(404, e.message))
    }

    @ExceptionHandler(DuplicateTripPlaceException::class)
    fun handleDuplicate(e: DuplicateTripPlaceException): ResponseEntity<ErrorResponse> {
        log.warn("Duplicate trip place Id: {}", e.message)
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(409, "이미 등록된 장소입니다"))
    }

    @ExceptionHandler(EmailVerificationException::class)
    fun handleEmailVerification(e: EmailVerificationException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(400, e.message))
    }

    @ExceptionHandler(MailCooldownException::class)
    fun handleMailCooldown(e: MailCooldownException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ErrorResponse(429, e.message))
    }

    @ExceptionHandler(InvalidFreeTimeMinutesException::class)
    fun handleInvalidFreeTimeMinutes(e: InvalidFreeTimeMinutesException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(400, e.message))
    }

    @ExceptionHandler(TripGroupSettingsLockedException::class)
    fun handleTripGroupSettingsLocked(e: TripGroupSettingsLockedException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(409, e.message))
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(e: RuntimeException): ResponseEntity<ErrorResponse> {
        log.error("Unhandled RuntimeException", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(500, e.message))
    }
}
