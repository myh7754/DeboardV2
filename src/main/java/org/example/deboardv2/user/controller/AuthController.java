package org.example.deboardv2.user.controller;


import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.user.config.JwtConfig;
import org.example.deboardv2.user.dto.*;
import org.example.deboardv2.user.service.AuthService;
import org.example.deboardv2.user.service.JwtTokenProvider;
import org.example.deboardv2.user.service.UserService;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @Operation(summary = "이메일 인증 코드 요청", description = "사용자가 입력한 이메일로 인증 코드를 발송합니다.")
    @GetMapping("/email/code")
    public ResponseEntity<?> validEmail(@RequestParam String email) {
        return ResponseEntity.ok().body(authService.sendEmailAuthCode(email));
    }

    @Operation(summary = "닉네임 중복 검사", description = "닉네임이 사용 가능한지 확인합니다.")
    @GetMapping("/valid/nickname")
    public ResponseEntity<?> validNickname(@RequestParam String nickname) {
        return ResponseEntity.ok().body(userService.checkNickname(nickname));
    }

    @Operation(summary = "이메일 인증 코드 검증", description = "사용자가 입력한 인증 코드가 올바른지 검증합니다.")
    @PostMapping("/email/verify")
    public ResponseEntity<?> validByNumber(@RequestParam String email, @RequestBody String code) {
        authService.validEmail(email, code);
        return ResponseEntity.ok().body("ok");
    }

    @Operation(summary = "회원가입", description = "신규 회원을 등록합니다.")
    @PostMapping("/signup")
    public ResponseEntity<?> signUp(@RequestBody SignupRequest signupRequest) {
        authService.signUp(signupRequest);
        return ResponseEntity.ok().body("ok");
    }

    @Operation(summary = "로그인", description = "사용자가 로그인하면 JWT 토큰을 발급하고 쿠키에 저장합니다.")
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

    @Operation(summary = "로그아웃", description = "Refresh/Access 토큰 쿠키를 제거하고 로그아웃 처리합니다.")
    @PostMapping("/refresh/logout")
    public ResponseEntity<?> logout(@CookieValue(name = "refreshToken", required = false) String refreshToken,
                                    @CookieValue(name = "accessToken",  required = false) String accessToken,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        authService.logout(refreshToken);

        // SecurityContext 초기화
        SecurityContextHolder.clearContext();

        // 세션 무효화 (혹시나 LiveSession 남아있으면 제거)
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
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

    @Operation(summary = "토큰 재발급", description = "Refresh 토큰을 사용하여 새로운 Access 토큰을 발급합니다.")
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

    @GetMapping("/{entityType}/{id}")
    public ResponseEntity<?> authCheck(@PathVariable String entityType, @PathVariable Long id) {
        authService.authCheck(id,  entityType);
        return ResponseEntity.ok().body("ok");
    }
}
