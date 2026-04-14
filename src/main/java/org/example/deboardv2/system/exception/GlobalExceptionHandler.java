package org.example.deboardv2.system.exception;


import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    // 필요한 에러로그
    // timestamp, // 로그백 설정에서 자동으로 채워줌
    // level, // 로그백 설정에서 자동으로 채워줌
    // logger (com.example.user.UserService), // 로그백 설정에서 자동으로 채워줌
    // thread (http-nio-8080-exec-1), // 로그백 설정에서 자동으로 채워줌
    // traceId // 내가 직접 mdc에 넣는값
    // statckTrace : logger.error("처리실패",ex) 라했을경우 스택트레이스를 출력
    // error message , exception stack trace
    // message : 사람이 읽을 수 있는 요약 메시지 // 내가 만돈 log.info( 메시지)

    private final ErrorCache errorCache;


    @ExceptionHandler(CustomException.class)
    public ResponseEntity<?> handleCustomException(CustomException e, HttpServletRequest request) {
        ErrorCode errorCode = e.getErrorCode();
        logException("warn", e, request);
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.getMessage(), errorCode.getStatus().value()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> globalException(Exception e, HttpServletRequest request) {
        logException("error", e, request);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("서버 내부 오류가 발생하였습니다.", 500));
    }

    private void logException(String level, Exception e, HttpServletRequest request) {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID);

        boolean isDuplicate = errorCache.isDuplicate(e, traceId);
        ErrorCache.ErrorInfo info = errorCache.getErrorInfo(e);
        int count = info.getCount().get();

        try {
            MDC.put("httpMethod", request.getMethod());
            MDC.put("httpUri", request.getRequestURI());
            MDC.put("exceptionType", e.getClass().getSimpleName());
            if (e instanceof CustomException ce) {
                MDC.put("errorCode", ce.getErrorCode().name());
            }

            if (!isDuplicate) {
                if ("warn".equals(level)) log.warn("request.exception", e);
                else                      log.error("request.exception", e);
            } else {
                MDC.put("firstTraceId", info.getFirstTraceId());
                MDC.put("duplicateCount", String.valueOf(count));
                if ("warn".equals(level)) log.warn("request.exception.duplicate");
                else                      log.error("request.exception.duplicate");
            }
        } finally {
            MDC.remove("httpMethod");
            MDC.remove("httpUri");
            MDC.remove("exceptionType");
            MDC.remove("errorCode");
            MDC.remove("firstTraceId");
            MDC.remove("duplicateCount");
        }
    }

}
