package csh.back.global.aspect

import csh.back.global.dto.ResponseData
import jakarta.servlet.http.HttpServletResponse
import org.aspectj.lang.ProceedingJoinPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class ResponseAspectTest {

    private val response = mock(HttpServletResponse::class.java)
    private val aspect = ResponseAspect(response)

    @Test
    @DisplayName("반환값이 ResponseData면 응답 상태코드를 statusCode로 설정한다")
    fun t1() {
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        val responseData = ResponseData(201, "created")
        `when`(joinPoint.proceed()).thenReturn(responseData)

        val result = aspect.handleResponse(joinPoint)

        verify(response).status = 201
        assertEquals(responseData, result)
    }

    @Test
    @DisplayName("반환값이 ResponseData가 아니면 응답 상태코드를 건드리지 않는다")
    fun t2() {
        val joinPoint = mock(ProceedingJoinPoint::class.java)
        `when`(joinPoint.proceed()).thenReturn("not a ResponseData")

        val result = aspect.handleResponse(joinPoint)

        verify(response, never()).status = anyInt()
        assertEquals("not a ResponseData", result)
    }
}