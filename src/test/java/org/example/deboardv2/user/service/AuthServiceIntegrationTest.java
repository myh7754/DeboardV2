package org.example.deboardv2.user.service;

import org.example.deboardv2.post.dto.PostCreateRequest;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.redis.RedisKeyConstants;
import org.example.deboardv2.redis.service.RedisService;
import org.example.deboardv2.system.exception.CustomException;
import org.example.deboardv2.system.exception.ErrorCode;
import org.example.deboardv2.user.dto.SignInRequest;
import org.example.deboardv2.user.dto.SignupRequest;
import org.example.deboardv2.user.dto.TokenBody;
import org.example.deboardv2.user.entity.Role;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.repository.UserRepository;
import org.example.deboardv2.user.service.impl.AuthServiceImpl;
import org.example.deboardv2.user.service.impl.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willDoNothing;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Rollback
class AuthServiceIntegrationTest {

    @Autowired
    private AuthServiceImpl authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisService redisService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PostRepository postRepository;

    @MockitoBean
    private MailService mailService;

    private static final String TEST_EMAIL = "integration-test@example.com";
    private static final String TEST_NICKNAME = "integTestUser";
    private static final String TEST_PASSWORD = "password123";

    @BeforeEach
    void setUp() {
        // MailService mock - 실제 메일 전송 방지
        willDoNothing().given(mailService).sendSimpleMailMessage(anyString(), anyString());
        // SecurityContext 초기화
        SecurityContextHolder.clearContext();
    }

    // ============================================================
    // signUp() 테스트
    // ============================================================

    @Test
    @DisplayName("signUp() - 이메일 미인증 상태에서 회원가입 시 EMAIL_NOT_VERIFIED 예외 발생")
    void signUp_이메일미인증_EMAIL_NOT_VERIFIED_예외() {
        // given
        SignupRequest request = new SignupRequest();
        request.setEmail(TEST_EMAIL);
        request.setNickname(TEST_NICKNAME);
        request.setPassword(TEST_PASSWORD);
        // Redis에 certified 키 없음 (미인증 상태)

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> authService.signUp(request));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    @DisplayName("signUp() - 이메일 인증 완료 후 정상 가입 시 User DB 저장 확인")
    void signUp_인증완료후_정상가입_DB저장() {
        // given
        redisService.setValue(RedisKeyConstants.EMAIL_AUTH + "certified:" + TEST_EMAIL, true);

        SignupRequest request = new SignupRequest();
        request.setEmail(TEST_EMAIL);
        request.setNickname(TEST_NICKNAME);
        request.setPassword(TEST_PASSWORD);

        // when
        User savedUser = authService.signUp(request);

        // then
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(savedUser.getNickname()).isEqualTo(TEST_NICKNAME);
        assertThat(userRepository.findByEmail(TEST_EMAIL)).isPresent();
    }

    @Test
    @DisplayName("signUp() - 닉네임 중복 시 NICKNAME_DUPLICATED 예외 발생")
    void signUp_닉네임중복_NICKNAME_DUPLICATED_예외() {
        // given - 동일 닉네임의 유저를 먼저 저장 (같은 트랜잭션 내에서 flush 필요)
        SignupRequest firstRequest = new SignupRequest();
        firstRequest.setEmail("first@example.com");
        firstRequest.setNickname(TEST_NICKNAME);
        firstRequest.setPassword(TEST_PASSWORD);
        redisService.setValue(RedisKeyConstants.EMAIL_AUTH + "certified:first@example.com", true);
        authService.signUp(firstRequest);

        // 두 번째 사용자: 같은 닉네임, 다른 이메일
        String duplicateEmail = "duplicate@example.com";
        redisService.setValue(RedisKeyConstants.EMAIL_AUTH + "certified:" + duplicateEmail, true);

        SignupRequest duplicateRequest = new SignupRequest();
        duplicateRequest.setEmail(duplicateEmail);
        duplicateRequest.setNickname(TEST_NICKNAME); // 동일 닉네임
        duplicateRequest.setPassword(TEST_PASSWORD);

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> authService.signUp(duplicateRequest));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NICKNAME_DUPLICATED);
    }

    // ============================================================
    // signIn() 테스트
    // ============================================================

    @Test
    @DisplayName("signIn() - 존재하지 않는 이메일로 로그인 시 EMAIL_MISMATCH 예외 발생")
    void signIn_존재하지않는이메일_EMAIL_MISMATCH_예외() {
        // given
        SignInRequest request = new SignInRequest();
        request.setEmail("notexist@example.com");
        request.setPassword(TEST_PASSWORD);

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> authService.signIn(request));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_MISMATCH);
    }

    @Test
    @DisplayName("signIn() - 비밀번호 불일치 시 PASSWORD_MISMATCH 예외 발생")
    void signIn_비밀번호불일치_PASSWORD_MISMATCH_예외() {
        // given - 사용자 사전 등록
        createTestUser(TEST_EMAIL, TEST_NICKNAME, TEST_PASSWORD);

        SignInRequest request = new SignInRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword("wrongPassword");

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> authService.signIn(request));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_MISMATCH);
    }

    @Test
    @DisplayName("signIn() - 정상 로그인 시 accessToken과 refreshToken 반환")
    void signIn_정상로그인_토큰반환() {
        // given
        createTestUser(TEST_EMAIL, TEST_NICKNAME, TEST_PASSWORD);

        SignInRequest request = new SignInRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword(TEST_PASSWORD);

        // when
        var loginResponse = authService.signIn(request);

        // then
        assertThat(loginResponse).isNotNull();
        assertThat(loginResponse.getJwtToken()).isNotNull();
        assertThat(loginResponse.getJwtToken().getAccessToken()).isNotBlank();
        assertThat(loginResponse.getJwtToken().getRefreshToken()).isNotBlank();
    }

    // ============================================================
    // sendEmailAuthCode() 테스트
    // ============================================================

    @Test
    @DisplayName("sendEmailAuthCode() - 이미 가입된 이메일이면 EMAIL_DUPLICATED 예외 발생")
    void sendEmailAuthCode_중복이메일_EMAIL_DUPLICATED_예외() {
        // given
        createTestUser(TEST_EMAIL, TEST_NICKNAME, TEST_PASSWORD);

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> authService.sendEmailAuthCode(TEST_EMAIL));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_DUPLICATED);
    }

    @Test
    @DisplayName("sendEmailAuthCode() - 신규 이메일이면 Redis에 인증 코드 저장 확인")
    void sendEmailAuthCode_신규이메일_Redis에_코드저장() {
        // given
        String newEmail = "newuser@example.com";

        // when
        authService.sendEmailAuthCode(newEmail);

        // then
        Object storedCode = redisService.getValue(RedisKeyConstants.EMAIL_AUTH + newEmail);
        assertThat(storedCode).isNotNull();
        assertThat(storedCode.toString()).hasSize(6);

        // 정리
        redisService.deleteValue(RedisKeyConstants.EMAIL_AUTH + newEmail);
    }

    // ============================================================
    // validEmail() 테스트
    // ============================================================

    @Test
    @DisplayName("validEmail() - 올바른 코드 입력 시 certified 키 저장 및 코드 키 삭제 확인")
    void validEmail_올바른코드_certified키_저장_코드키_삭제() {
        // given
        String verifyEmail = "verify@example.com";
        String code = "123456";
        redisService.setValueWithExpire(RedisKeyConstants.EMAIL_AUTH + verifyEmail, code, Duration.ofMinutes(3));

        // when
        Boolean result = authService.validEmail(verifyEmail, code);

        // then
        assertThat(result).isTrue();
        assertThat(redisService.getValue(RedisKeyConstants.EMAIL_AUTH + "certified:" + verifyEmail)).isNotNull();
        assertThat(redisService.getValue(RedisKeyConstants.EMAIL_AUTH + verifyEmail)).isNull();

        // 정리
        redisService.deleteValue(RedisKeyConstants.EMAIL_AUTH + "certified:" + verifyEmail);
    }

    @Test
    @DisplayName("validEmail() - 잘못된 코드 입력 시 EMAIL_VERIFICATION_ERROR 예외 발생")
    void validEmail_잘못된코드_EMAIL_VERIFICATION_ERROR_예외() {
        // given
        String verifyEmail = "verify2@example.com";
        String correctCode = "654321";
        String wrongCode = "000000";
        redisService.setValueWithExpire(RedisKeyConstants.EMAIL_AUTH + verifyEmail, correctCode, Duration.ofMinutes(3));

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> authService.validEmail(verifyEmail, wrongCode));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_VERIFICATION_ERROR);

        // 정리
        redisService.deleteValue(RedisKeyConstants.EMAIL_AUTH + verifyEmail);
    }

    // ============================================================
    // reissue() 테스트
    // ============================================================

    @Test
    @DisplayName("reissue() - 유효한 refreshToken으로 새 accessToken 반환")
    void reissue_유효한refreshToken_새accessToken반환() {
        // given - 정상 로그인으로 refresh 토큰 획득
        createTestUser(TEST_EMAIL, TEST_NICKNAME, TEST_PASSWORD);

        SignInRequest signInRequest = new SignInRequest();
        signInRequest.setEmail(TEST_EMAIL);
        signInRequest.setPassword(TEST_PASSWORD);
        var loginResponse = authService.signIn(signInRequest);
        String refreshToken = loginResponse.getJwtToken().getRefreshToken();

        // when
        String newAccessToken = authService.reissue(refreshToken);

        // then
        assertThat(newAccessToken).isNotBlank();
    }

    @Test
    @DisplayName("reissue() - 블랙리스트에 등록된 refreshToken이면 INVALID_REFRESH_TOKEN 예외 발생")
    void reissue_블랙리스트_refreshToken_INVALID_REFRESH_TOKEN_예외() {
        // given - 정상 로그인 후 로그아웃으로 블랙리스트 등록
        createTestUser(TEST_EMAIL, TEST_NICKNAME, TEST_PASSWORD);

        SignInRequest signInRequest = new SignInRequest();
        signInRequest.setEmail(TEST_EMAIL);
        signInRequest.setPassword(TEST_PASSWORD);
        var loginResponse = authService.signIn(signInRequest);
        String refreshToken = loginResponse.getJwtToken().getRefreshToken();

        // 로그아웃 → 블랙리스트 등록
        authService.logout(refreshToken);

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> authService.reissue(refreshToken));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    // ============================================================
    // logout() 테스트
    // ============================================================

    @Test
    @DisplayName("logout() - refreshToken을 Redis 블랙리스트에 저장한다")
    void logout_refreshToken_Redis_블랙리스트_저장() {
        // given
        createTestUser(TEST_EMAIL, TEST_NICKNAME, TEST_PASSWORD);

        SignInRequest signInRequest = new SignInRequest();
        signInRequest.setEmail(TEST_EMAIL);
        signInRequest.setPassword(TEST_PASSWORD);
        var loginResponse = authService.signIn(signInRequest);
        String refreshToken = loginResponse.getJwtToken().getRefreshToken();

        // 토큰에서 userId 파싱 (블랙리스트 키 확인용)
        User savedUser = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        Long userId = savedUser.getId();

        // when
        authService.logout(refreshToken);

        // then
        Object blacklisted = redisService.getValue(RedisKeyConstants.REFRESH_TOKEN + userId);
        assertThat(blacklisted).isNotNull();
        assertThat(blacklisted.toString()).isEqualTo(refreshToken);
    }

    // ============================================================
    // authCheck() 테스트
    // ============================================================

    @Test
    @DisplayName("authCheck() - POST 작성자 본인이면 예외 없이 통과한다")
    void authCheck_POST_작성자본인_통과() {
        // given
        User author = createTestUser(TEST_EMAIL, TEST_NICKNAME, TEST_PASSWORD);
        setSecurityContext(author.getId());

        PostCreateRequest postDto = new PostCreateRequest("테스트 게시글", "내용");
        Post post = Post.from(postDto, author);
        Post savedPost = postRepository.save(post);

        // when & then (예외 없이 통과)
        authService.authCheck(savedPost.getId(), "POST");
    }

    @Test
    @DisplayName("authCheck() - POST 작성자가 아닌 타인이면 FORBIDDEN 예외 발생")
    void authCheck_POST_타인_FORBIDDEN_예외() {
        // given
        User author = createTestUser(TEST_EMAIL, TEST_NICKNAME, TEST_PASSWORD);
        User other = createTestUser("other@example.com", "otherUser", TEST_PASSWORD);

        PostCreateRequest postDto = new PostCreateRequest("테스트 게시글", "내용");
        Post post = Post.from(postDto, author);
        Post savedPost = postRepository.save(post);

        // 타인의 SecurityContext 설정
        setSecurityContext(other.getId());

        // when & then
        CustomException exception = assertThrows(CustomException.class,
                () -> authService.authCheck(savedPost.getId(), "POST"));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    // ============================================================
    // 헬퍼 메서드
    // ============================================================

    private User createTestUser(String email, String nickname, String rawPassword) {
        SignupRequest request = new SignupRequest();
        request.setEmail(email);
        request.setNickname(nickname);
        request.setPassword(passwordEncoder.encode(rawPassword));
        return userRepository.save(User.toEntity(request));
    }

    private void setSecurityContext(Long userId) {
        TokenBody tokenBody = new TokenBody(userId, "user", Role.ROLE_MEMBER);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                tokenBody, null, Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
