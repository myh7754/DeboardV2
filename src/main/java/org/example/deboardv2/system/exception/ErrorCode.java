package org.example.deboardv2.system.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    //user
    USER_NOT_FOUND(HttpStatus.BAD_REQUEST, "해당 회원을 찾을 수 없습니다."),

    //auth
    //400
    NICKNAME_DUPLICATED(HttpStatus.BAD_REQUEST, "사용중인 닉네임 입니다."),
    EMAIL_DUPLICATED(HttpStatus.BAD_REQUEST, "가입된 이메일 입니다."),
    EMAIL_VERIFICATION_ERROR(HttpStatus.BAD_REQUEST, "이메일 인증이 실패하였습니다."),
    OAUTH2_UNKNOWN_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 Oauth2 provider입니다"),
    //401
    LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    EMAIL_MISMATCH(HttpStatus.UNAUTHORIZED, "계정이 일치하지 않습니다"),
    PASSWORD_MISMATCH(HttpStatus.UNAUTHORIZED, "비밀번호가 일치하지 않습니다"),
    EMAIL_NOT_VERIFIED( HttpStatus.UNAUTHORIZED,"이메일 인증이 필요합니다."),
    //403
    FORBIDDEN(HttpStatus.FORBIDDEN, "해당 권한이 없습니다"),

    //jwt
    EXPIRED_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 ACCESS토큰 입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 만료되어 로그아웃 합니다."),

    //post
    POST_NOT_FOUND(HttpStatus.BAD_REQUEST, "게시글을 찾을 수 없습니다"),

    //comment
    COMMENT_NOT_FOUND(HttpStatus.BAD_REQUEST, "해당 댓글을 찾을 수 없습니다"),

    //
    DUPLICATED_FEED(HttpStatus.BAD_REQUEST, "중복된 피드 입니다."),
    DUPLICATED_USER_FEED(HttpStatus.BAD_REQUEST, "중복된 유저 피드 입니다");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.status = httpStatus;
        this.message = message;
    }
}
