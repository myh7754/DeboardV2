package org.example.deboardv2.user.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.user.config.JwtConfig;
import org.example.deboardv2.user.dto.TokenBody;
import org.example.deboardv2.user.entity.Role;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;


@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    private final JwtConfig jwtConfig;

    // 비밀키 생성
    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(jwtConfig.getSecret().getAppKey().getBytes());
    }

    // jwt 생성
    public String issue(TokenBody user , Duration validTime) {
        // subject : 토큰의 주체 "누구에 대한 토큰인가?"
        // claim : 해당 토큰에 담기는 추가 정보들 역할, 이메일 등
        return Jwts.builder()
                .subject(user.getMemberId().toString())
                .claim("role", user.getRole())
                .issuedAt(new Date())
                .expiration(new Date(new Date().getTime() + validTime.toMillis()))
                .signWith(getSecretKey(), Jwts.SIG.HS256)
                .compact();
    }

    //쿠키를 이용하여 토큰 담기
    public ResponseCookie tokenAddCookie(String tokenName,String token, Duration expired) {
        // 여기서 secure=true인데 로컬 환경에서 https가 아니면 브라우저가 쿠키를 저장하지 않음
        String path;
        if (tokenName.equals("accessToken")) {
            path="/";
        } else {
            path = "/api/auth/refresh";
        }
        return ResponseCookie.from(tokenName, token)
                .httpOnly(true)
                .secure(false)
                .path(path)
                .sameSite("Strict")
                .maxAge(expired)
                .build();
    }

    // 토큰 검증
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSecretKey()) // 검증용 키 설정
                    .build()
                    .parseSignedClaims(token); // jwt 토큰을 파싱하여 서명된 클레임(payload)을 추출하는 메서드
            return true;
        } catch (JwtException e) {
            log.debug("Invalid JWT Token Detected. msg = {}", e.getMessage());
            log.debug("TOKEN : {}", token);
        } catch (IllegalArgumentException e) {
            log.debug("JWT claims String is empty = {}", e.getMessage());
        } catch (Exception e) {
            log.error("an error occurred while validating the token. err msg = {}", e.getMessage());
        }
        return false;
    }

    // 검증 후 토큰의 정보 추출
    public TokenBody parseJwt(String token) {
        Jws<Claims> parsed = Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token);

        String subject = parsed.getPayload().getSubject();
        String roleStr = parsed.getPayload().get("role", String.class);
        Role role = Role.valueOf(roleStr);
        return new TokenBody(Long.parseLong(subject), role);
    }
}
