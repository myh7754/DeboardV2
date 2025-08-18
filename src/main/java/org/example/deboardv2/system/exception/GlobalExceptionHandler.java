package org.example.deboardv2.system.exception;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 기본 예외처리
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<?> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.getMessage(), errorCode.getStatus().value()));
    }

    // 기타 예외처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> globalException(Exception e) {
        log.error("Unhandled Exception: ", e);
        return ResponseEntity
                .status(500)
                .body(new ErrorResponse("서버 내부 오류가 발생하였습니다.", 500));
    }
}
