package csh.back.global.aspect;

import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import csh.back.global.dto.ResponseData;

@Aspect
@Component
public class ResponseAspect {
    private final HttpServletResponse response;

    public ResponseAspect(HttpServletResponse response) {
        this.response = response;
    }

    @Around("""
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
            """)
    public Object handleResponse(ProceedingJoinPoint joinPoint) throws Throwable {
        Object proceed = joinPoint.proceed();
        if (proceed instanceof ResponseData<?> rsData) {
            response.setStatus(rsData.statusCode());
        }
        return proceed;
    }
}
