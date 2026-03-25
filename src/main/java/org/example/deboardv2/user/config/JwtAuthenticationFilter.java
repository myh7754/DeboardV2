package org.example.deboardv2.user.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.user.dto.TokenBody;
import org.example.deboardv2.user.entity.Role;
import org.example.deboardv2.user.service.JwtTokenProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 로그인, 회원가입, 토큰 재발급 엔드포인트는 필터 스킵
        String requestURI = request.getRequestURI();
        if (requestURI.startsWith("/api/auth/signin") || 
            requestURI.startsWith("/api/auth/signup") || 
            requestURI.startsWith("/api/auth/refresh")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String token = resolveTokenCookie(request);
        if (token != null && jwtTokenProvider.validateToken(token)) {
            try {
                TokenBody tokenBody = jwtTokenProvider.parseJwt(token);
                Long memberId = tokenBody.getMemberId();
                Role role = tokenBody.getRole();
                List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role.name()));

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        tokenBody,
                        token,
                        authorities
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // 토큰 파싱 실패 시 인증 정보 설정하지 않고 계속 진행
                log.debug("JWT 토큰 파싱 실패: {}", e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveTokenCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if(cookies != null) {
            for(Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

}
