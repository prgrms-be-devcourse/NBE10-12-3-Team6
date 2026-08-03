package csh.back.global.exception

import csh.back.domain.member.exception.EmailVerificationException
import csh.back.domain.member.exception.ExistingMemberException
import csh.back.domain.member.exception.InvalidCredentialsException
import csh.back.domain.member.exception.InvalidPasswordException
import csh.back.domain.member.exception.KakaoMemberPasswordChangeException
import csh.back.domain.member.exception.LoginLockedException
import csh.back.domain.trip.group.exception.NonMemberException
import csh.back.domain.trip.group.exception.NotFoundException
import csh.back.domain.trip.group.settings.exception.InvalidFreeTimeMinutesException
import csh.back.domain.trip.group.settings.exception.TripGroupSettingsLockedException
import csh.back.domain.trip.place.exception.DuplicateTripPlaceException
import csh.back.domain.trip.place.exception.TripAlreadyStartedException
import csh.back.domain.trip.place.exception.WishPlaceInUseException
import csh.back.global.dto.ErrorResponse
import csh.back.global.mail.exception.MailCooldownException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExeptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(NotFoundException::class)
    fun handleGroupNotFound(
        e: NotFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<*> {
        if (request.acceptsEventStream()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build<Void>()
        }

        return handleGroupNotFound(e)
    }

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
    fun handleGroupNotFound(
        e: NonMemberException,
        request: HttpServletRequest,
    ): ResponseEntity<*> {
        if (request.acceptsEventStream()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Void>()
        }

        return handleGroupNotFound(e)
    }

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

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(e: InvalidCredentialsException): ResponseEntity<ErrorResponse> {
        // remainingAttempts는 실제 회원의 비번 오류일 때만 채워짐 (미존재 이메일 케이스는 null 유지)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(401, e.message, remainingAttempts = e.remainingAttempts))
    }

    @ExceptionHandler(LoginLockedException::class)
    fun handleLoginLocked(e: LoginLockedException): ResponseEntity<ErrorResponse> {
        // retryAfterSeconds를 body에 포함 → 프론트가 초 단위 카운트다운에 사용
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ErrorResponse(429, e.message, retryAfterSeconds = e.retryAfterSeconds))
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

    @ExceptionHandler(WishPlaceInUseException::class)
    fun handleWishPlaceInUse(e: WishPlaceInUseException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(409, e.message))
    }

    @ExceptionHandler(TripAlreadyStartedException::class)
    fun handleTripAlreadyStarted(e: TripAlreadyStartedException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(400, e.message))
    }

    @ExceptionHandler(InvalidPasswordException::class)
    fun handleInvalidPassword(e: InvalidPasswordException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(400, e.message))
    }

    @ExceptionHandler(KakaoMemberPasswordChangeException::class)
    fun handleKakaoMemberPasswordChange(e: KakaoMemberPasswordChangeException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(403, e.message))
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(e: RuntimeException): ResponseEntity<ErrorResponse> {
        log.error("Unhandled RuntimeException", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(500, e.message))
    }

    private fun HttpServletRequest.acceptsEventStream(): Boolean =
        getHeader(HttpHeaders.ACCEPT)
            ?.contains(MediaType.TEXT_EVENT_STREAM_VALUE) == true
}
