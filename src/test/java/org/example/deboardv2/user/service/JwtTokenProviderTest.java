package org.example.deboardv2.user.service;

import org.example.deboardv2.user.config.JwtConfig;
import org.example.deboardv2.user.dto.TokenBody;
import org.example.deboardv2.user.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String TEST_APP_KEY =
            "4B398FBF0C39348DFD0E7188B151F3F734ACCD37006BCA207077FCA52701E3D5";
    private static final String DIFFERENT_APP_KEY =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private JwtTokenProvider jwtTokenProvider;
    private JwtTokenProvider differentKeyProvider;

    @BeforeEach
    void setUp() {
        JwtConfig.Validation validation = new JwtConfig.Validation(
                Duration.ofMinutes(30),
                Duration.ofHours(1)
        );
        JwtConfig.Secret secret = new JwtConfig.Secret(TEST_APP_KEY, "origin-key");
        JwtConfig jwtConfig = new JwtConfig(validation, secret);

        jwtTokenProvider = new JwtTokenProvider(jwtConfig);
        ReflectionTestUtils.setField(jwtTokenProvider, "cookieSecure", false);

        // 다른 시크릿 키를 가진 provider (잘못된 서명 검증용)
        JwtConfig.Secret differentSecret = new JwtConfig.Secret(DIFFERENT_APP_KEY, "origin-key");
        JwtConfig differentJwtConfig = new JwtConfig(validation, differentSecret);
        differentKeyProvider = new JwtTokenProvider(differentJwtConfig);
        ReflectionTestUtils.setField(differentKeyProvider, "cookieSecure", false);
    }

    @Test
    @DisplayName("issue() - 생성된 토큰에서 subject(userId)와 role claim 정상 파싱")
    void issue_토큰생성후_subject와_role_정상파싱() {
        // given
        TokenBody tokenBody = new TokenBody(42L, "testUser", Role.ROLE_MEMBER);

        // when
        String token = jwtTokenProvider.issue(tokenBody, Duration.ofMinutes(30));

        // then
        assertThat(token).isNotNull();
        TokenBody parsed = jwtTokenProvider.parseJwt(token);
        assertThat(parsed.getMemberId()).isEqualTo(42L);
        assertThat(parsed.getRole()).isEqualTo(Role.ROLE_MEMBER);
    }

    @Test
    @DisplayName("validateToken() - 유효한 토큰은 true를 반환한다")
    void validateToken_유효한토큰_true() {
        // given
        TokenBody tokenBody = new TokenBody(1L, "user", Role.ROLE_MEMBER);
        String token = jwtTokenProvider.issue(tokenBody, Duration.ofMinutes(30));

        // when
        boolean result = jwtTokenProvider.validateToken(token);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("validateToken() - 만료된 토큰은 false를 반환한다")
    void validateToken_만료된토큰_false() {
        // given
        TokenBody tokenBody = new TokenBody(1L, "user", Role.ROLE_MEMBER);
        // 만료 시간을 -1ms로 설정하여 즉시 만료된 토큰 생성
        String expiredToken = jwtTokenProvider.issue(tokenBody, Duration.ofMillis(-1));

        // when
        boolean result = jwtTokenProvider.validateToken(expiredToken);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("validateToken() - 잘못된 서명의 토큰은 false를 반환한다")
    void validateToken_잘못된서명_false() {
        // given
        TokenBody tokenBody = new TokenBody(1L, "user", Role.ROLE_MEMBER);
        // 다른 키로 서명된 토큰을 원래 키로 검증 시도
        String wrongSignatureToken = differentKeyProvider.issue(tokenBody, Duration.ofMinutes(30));

        // when
        boolean result = jwtTokenProvider.validateToken(wrongSignatureToken);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("parseJwt() - subject(userId)와 role을 정확히 추출한다")
    void parseJwt_subject와_role_정확히추출() {
        // given
        TokenBody tokenBody = new TokenBody(99L, "admin", Role.ROLE_ADMIN);
        String token = jwtTokenProvider.issue(tokenBody, Duration.ofMinutes(30));

        // when
        TokenBody parsed = jwtTokenProvider.parseJwt(token);

        // then
        assertThat(parsed.getMemberId()).isEqualTo(99L);
        assertThat(parsed.getRole()).isEqualTo(Role.ROLE_ADMIN);
    }

    @Test
    @DisplayName("tokenAddCookie() - accessToken은 path가 '/'이다")
    void tokenAddCookie_accessToken_path는_루트() {
        // given
        String tokenValue = "some-access-token";

        // when
        ResponseCookie cookie = jwtTokenProvider.tokenAddCookie("accessToken", tokenValue, Duration.ofMinutes(30));

        // then
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getName()).isEqualTo("accessToken");
    }

    @Test
    @DisplayName("tokenAddCookie() - refreshToken은 path가 '/api/auth/refresh'이다")
    void tokenAddCookie_refreshToken_path는_리프레시엔드포인트() {
        // given
        String tokenValue = "some-refresh-token";

        // when
        ResponseCookie cookie = jwtTokenProvider.tokenAddCookie("refreshToken", tokenValue, Duration.ofHours(1));

        // then
        assertThat(cookie.getPath()).isEqualTo("/api/auth/refresh");
        assertThat(cookie.getName()).isEqualTo("refreshToken");
    }

    @Test
    @DisplayName("tokenAddCookie() - httpOnly=true, sameSite='Strict' 속성이 설정된다")
    void tokenAddCookie_httpOnly와_sameSite_속성확인() {
        // when
        ResponseCookie cookie = jwtTokenProvider.tokenAddCookie("accessToken", "token-value", Duration.ofMinutes(30));

        // then
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
    }
}
