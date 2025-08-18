package org.example.deboardv2.system.exception;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {
    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    // provider의 경우 메시지 추가
    public CustomException(ErrorCode errorCode, String message) {
        super(errorCode.getMessage() +": "+ message);
        this.errorCode = errorCode;
    }
}
