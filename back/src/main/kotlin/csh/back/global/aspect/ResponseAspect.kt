package csh.back.global.aspect

import csh.back.global.dto.ResponseData
import jakarta.servlet.http.HttpServletResponse
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

@Aspect
@Component
class ResponseAspect(
    private val response: HttpServletResponse
) {
    @Around(
        """
        execution(public csh.back.global.dto.ResponseData *(..)) &&
        (
            within(@org.springframework.stereotype.Controller *) ||
            within(@org.springframework.web.bind.annotation.RestController *)
        ) &&
        (
            @annotation(org.springframework.web.bind.annotation.GetMapping) ||
            @annotation(org.springframework.web.bind.annotation.PostMapping) ||
            @annotation(org.springframework.web.bind.annotation.PatchMapping) ||
            @annotation(org.springframework.web.bind.annotation.PutMapping) ||
            @annotation(org.springframework.web.bind.annotation.DeleteMapping) ||
            @annotation(org.springframework.web.bind.annotation.RequestMapping)
        )
        """
    )
    fun handleResponse(joinPoint: ProceedingJoinPoint): Any? {
        val proceed = joinPoint.proceed()
        if (proceed is ResponseData<*>) {
            response.status = proceed.statusCode
        }
        return proceed
    }
}