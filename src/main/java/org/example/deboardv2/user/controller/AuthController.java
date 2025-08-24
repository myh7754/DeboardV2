package org.example.deboardv2.user.controller;


import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.user.config.JwtConfig;
import org.example.deboardv2.user.dto.*;
import org.example.deboardv2.user.service.AuthService;
import org.example.deboardv2.user.service.JwtTokenProvider;
import org.example.deboardv2.user.service.UserService;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {
    public final AuthService authService;
    public final JwtTokenProvider jwtTokenProvider;
    public final JwtConfig jwtConfig;
    private final UserService userService;

    @GetMapping("/email/code")
    public ResponseEntity<?> validEmail(@RequestParam String email) {
        log.info("이메일 중복 체크 검사 {}", email);
        return ResponseEntity.ok().body(authService.sendEmailAuthCode(email));
    }

    @GetMapping("/valid/nickname")
    public ResponseEntity<?> validNickname(@RequestParam String nickname) {
        log.info("닉네임 중복체크 들어옴 {}", nickname);
        return ResponseEntity.ok().body(userService.checkNickname(nickname));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<?> validByNumber(@RequestParam String email, @RequestBody String code) {
        log.info("이메일 인증 코드 검사 {}", code);
        authService.validEmail(email, code);
        return ResponseEntity.ok().body("ok");
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signUp(@RequestBody SignupRequest signupRequest) {
        authService.signUp(signupRequest);
        return ResponseEntity.ok().body("ok");
    }

    @PostMapping("/signin")
    public ResponseEntity<?> signIn(@RequestBody SignInRequest signInRequest, HttpServletResponse response) {
        LoginResponse loginResponse = authService.signIn(signInRequest);
        JwtToken jwtToken = loginResponse.getJwtToken();
        // 쿠키에 access 저장
        ResponseCookie accessCookie = jwtTokenProvider.tokenAddCookie("accessToken",jwtToken.getAccessToken(), jwtConfig.getValidation().getAccess());
        //ResponseCookie는 addHeader로 직접 넣어줘야함
        response.addHeader("Set-Cookie", accessCookie.toString());

        // 쿠키에 refresh 저장
        ResponseCookie refreshCookie = jwtTokenProvider.tokenAddCookie("refreshToken",jwtToken.getRefreshToken(), jwtConfig.getValidation().getRefresh());
        //ResponseCookie는 addHeader로 직접 넣어줘야함
        response.addHeader("Set-Cookie", refreshCookie.toString());
//        UserDto userDto = loginResponse.getUserDto();
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/refresh/logout")
    public ResponseEntity<?> logout(@CookieValue(name = "refreshToken", required = false) String refreshToken,
                                    @CookieValue(name = "accessToken",  required = false) String accessToken,
                                    HttpServletResponse response) {
        authService.logout(refreshToken);
        // 쿠키 초기화
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken","")
                .maxAge(0)
                .httpOnly(true)
                .secure(false)
                .path("/api/auth/refresh")
                .sameSite("Lax")
                .build();
        ResponseCookie accessCookie = ResponseCookie.from("accessToken","")
                .maxAge(0)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Lax")
                .build();

        response.addHeader("Set-Cookie", accessCookie.toString());
        response.addHeader("Set-Cookie", refreshCookie.toString());
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> reissue(@CookieValue(name = "refreshToken", required = false)  String refreshToken,
                                     HttpServletResponse response) {
        String reissue = authService.reissue(refreshToken);
        // 쿠키에 access 저장
        ResponseCookie accessCookie = jwtTokenProvider.tokenAddCookie("accessToken",reissue, jwtConfig.getValidation().getAccess());
        //ResponseCookie는 addHeader로 직접 넣어줘야함
        response.addHeader("Set-Cookie", accessCookie.toString());


        return ResponseEntity.ok().body("ok");
    }
}
