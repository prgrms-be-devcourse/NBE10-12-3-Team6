package csh.back.global.exception

import csh.back.domain.member.exception.ExistingMemberException
import csh.back.domain.trip.group.exception.NonMemberException
import csh.back.domain.trip.group.exception.NotFoundException
import csh.back.domain.trip.place.exception.DuplicateTripPlaceException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.validation.BindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException

class GlobalExeptionHandlerTest {

    private val handler = GlobalExeptionHandler()

    @Test
    @DisplayName("NotFoundException 발생 시 404와 메시지를 반환한다")
    fun t1() {
        val response = handler.handleGroupNotFound(NotFoundException("모임을 찾을 수 없습니다"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(404, response.body?.statusCode)
        assertEquals("모임을 찾을 수 없습니다", response.body?.message)
    }

    @Test
    @DisplayName("NonMemberException 발생 시 403과 메시지를 반환한다")
    fun t2() {
        val response = handler.handleGroupNotFound(NonMemberException("모임 멤버가 아닙니다"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(403, response.body?.statusCode)
        assertEquals("모임 멤버가 아닙니다", response.body?.message)
    }

    @Test
    @DisplayName("ExistingMemberException 발생 시 404와 메시지를 반환한다")
    fun t3() {
        val response = handler.handleGroupNotFound(ExistingMemberException("이미 존재하는 회원입니다"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(404, response.body?.statusCode)
        assertEquals("이미 존재하는 회원입니다", response.body?.message)
    }

    @Test
    @DisplayName("DuplicateTripPlaceException 발생 시 409와 고정 메시지를 반환한다")
    fun t4() {
        val response = handler.handleDuplicate(DuplicateTripPlaceException("1"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(409, response.body?.statusCode)
        assertEquals("이미 등록된 장소입니다", response.body?.message)
    }

    @Test
    @DisplayName("RuntimeException 발생 시 500과 메시지를 반환한다")
    fun t5() {
        val response = handler.handleRuntimeException(RuntimeException("알 수 없는 오류"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals(500, response.body?.statusCode)
        assertEquals("알 수 없는 오류", response.body?.message)
    }

    @Test
    @DisplayName("검증 실패 시 400과 필드명 포함 메시지를 반환한다")
    fun t6() {
        val fieldError = FieldError("request", "region", "공백일 수 없습니다")
        val bindingResult = mock(BindingResult::class.java)
        `when`(bindingResult.fieldErrors).thenReturn(listOf(fieldError))
        val exception = MethodArgumentNotValidException(mock(MethodParameter::class.java), bindingResult)

        val response = handler.handleValidation(exception)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(400, response.body?.statusCode)
        assertEquals("region: 공백일 수 없습니다", response.body?.message)
    }
}