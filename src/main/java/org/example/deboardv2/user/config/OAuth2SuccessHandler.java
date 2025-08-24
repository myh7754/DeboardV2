package org.example.deboardv2.user.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.redis.service.RedisService;
import org.example.deboardv2.user.dto.MemberDetails;
import org.example.deboardv2.user.dto.TokenBody;
import org.example.deboardv2.user.entity.Role;
import org.example.deboardv2.user.service.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtConfig jwtConfig;
    private final RedisService redisService;
    @Value("${custom.front.redirect-url}")
    String targetUrl;


    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        MemberDetails memberDetails = (MemberDetails) authentication.getPrincipal();
        TokenBody tokenBody = new TokenBody(memberDetails.getId(), Role.valueOf(memberDetails.getRole()));
        log.info("oauth memberDetails {}",  memberDetails);

        // access token
        String access = jwtTokenProvider.issue(tokenBody, jwtConfig.getValidation().getAccess());
        String refresh = jwtTokenProvider.issue(tokenBody, jwtConfig.getValidation().getRefresh());

        // 쿠키에 access 저장
        ResponseCookie accessCookie = jwtTokenProvider.tokenAddCookie("accessToken",access, jwtConfig.getValidation().getAccess());
        //ResponseCookie는 addHeader로 직접 넣어줘야함
        response.addHeader("Set-Cookie", accessCookie.toString());
        // 쿠키에 refresh 저장
        ResponseCookie refreshCookie = jwtTokenProvider.tokenAddCookie("refreshToken",refresh, jwtConfig.getValidation().getRefresh());
        response.addHeader("Set-Cookie", refreshCookie.toString());

        // redis에 refresh 저장
        redisService.setValueWithExpire("refresh:"+memberDetails.getId(), refresh, jwtConfig.getValidation().getRefresh());

        getRedirectStrategy().sendRedirect(request,response,targetUrl);
    }
}
