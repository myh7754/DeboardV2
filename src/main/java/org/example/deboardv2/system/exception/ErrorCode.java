package org.example.deboardv2.system.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    //user
    USER_NOT_FOUND(HttpStatus.BAD_REQUEST, "멤버를 찾을 수 없습니다."),

    //auth
    PASSWORD_MISSMATCH(HttpStatus.UNAUTHORIZED, "비밀번호가 일치하지 않습니다"),
    EMAIL_DUPLICATED(HttpStatus.BAD_REQUEST, "가입된 이메일 입니다."),
    EMAIL_VERIFICATION_ERROR(HttpStatus.BAD_REQUEST, "이메일 인증이 실패하였습니다."),
    EMAIL_NOT_VERIFIED( HttpStatus.UNAUTHORIZED,"이메일 인증이 필요합니다."),
    OAUTH2_UNKNOWN_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 Oauth2 provider입니다"),

    //jwt
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유요하지 않은 리프레시 토큰입니다."),

    //post
    POST_NOT_FOUND(HttpStatus.BAD_REQUEST, "게시글을 찾을 수 없습니다");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {

        this.status = httpStatus;
        this.message = message;
    }
}
