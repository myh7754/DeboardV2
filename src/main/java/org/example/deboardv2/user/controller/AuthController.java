package org.example.deboardv2.user.controller;


import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.deboardv2.user.config.JwtConfig;
import org.example.deboardv2.user.dto.JwtToken;
import org.example.deboardv2.user.dto.SignInRequest;
import org.example.deboardv2.user.dto.SignupRequest;
import org.example.deboardv2.user.service.AuthService;
import org.example.deboardv2.user.service.JwtTokenProvider;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    public final AuthService authService;
    public final JwtTokenProvider jwtTokenProvider;
    public final JwtConfig jwtConfig;

    @PostMapping("/email/code")
    public ResponseEntity<?> validEmail(@RequestParam String email) {
        return ResponseEntity.ok().body(authService.sendEmailAuthCode(email));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<?> validByNumber(@RequestParam String email, @RequestBody String code) {
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
        JwtToken jwtToken = authService.signIn(signInRequest);
        // 쿠키에 access 저장
        ResponseCookie accessCookie = jwtTokenProvider.tokenAddCookie(jwtToken.getAccessToken(), jwtConfig.getValidation().getAccess());
        //ResponseCookie는 addHeader로 직접 넣어줘야함
        response.addHeader("Set-Cookie", accessCookie.toString());

        // 쿠키에 refresh 저장
        ResponseCookie refreshCookie = jwtTokenProvider.tokenAddCookie(jwtToken.getRefreshToken(), jwtConfig.getValidation().getRefresh());
        //ResponseCookie는 addHeader로 직접 넣어줘야함
        response.addHeader("Set-Cookie", refreshCookie.toString());
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(name = "refreshToken", required = false) String refreshToken,
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
        ResponseCookie accessCookie = jwtTokenProvider.tokenAddCookie(reissue, jwtConfig.getValidation().getAccess());
        //ResponseCookie는 addHeader로 직접 넣어줘야함
        response.addHeader("Set-Cookie", accessCookie.toString());


        return ResponseEntity.ok().body("ok");
    }
}
