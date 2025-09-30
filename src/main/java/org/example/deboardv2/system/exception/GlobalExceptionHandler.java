package org.example.deboardv2.system.exception;


import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.system.config.TraceIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;


@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<?> handleCustomException(CustomException e, HttpServletRequest request) throws ClassNotFoundException {
        ErrorCode errorCode = e.getErrorCode();
        logException("warn", e, request);

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.getMessage(), errorCode.getStatus().value()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> globalException(Exception e, HttpServletRequest request) throws ClassNotFoundException {
        logException("error",e,request);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("서버 내부 오류가 발생하였습니다.", 500));
    }

    private void logException(String level ,Exception e, HttpServletRequest request) throws ClassNotFoundException {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID);
        StackTraceElement element = e.getStackTrace()[0];
        String className = Class.forName(element.getClassName()).getSimpleName();
        String methodName = element.getMethodName();
        int lineNumber = element.getLineNumber();
        String logMessage = String.format(
                "[%s] ERROR %s %s (%s.%s) (line %d) - %s: %s",
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                className,
                methodName,
                lineNumber,
                e.getClass().getSimpleName(),
                e.getMessage()
        );
        if ("warn".equals(level)) {
            log.warn(logMessage, e);
        } else {
            log.error(logMessage, e);
        }
    }
}
