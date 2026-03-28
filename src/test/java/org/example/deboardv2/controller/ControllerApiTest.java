package org.example.deboardv2.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.example.deboardv2.comment.service.CommentsService;
import org.example.deboardv2.post.service.PostService;
import org.example.deboardv2.rss.service.FeedService;
import org.example.deboardv2.search.service.SearchService;
import org.example.deboardv2.user.config.JwtConfig;
import org.example.deboardv2.user.dto.JwtToken;
import org.example.deboardv2.user.dto.LoginResponse;
import org.example.deboardv2.user.dto.SignInRequest;
import org.example.deboardv2.user.dto.TokenBody;
import org.example.deboardv2.user.entity.Role;
import org.example.deboardv2.user.service.AuthService;
import org.example.deboardv2.user.service.JwtTokenProvider;
import org.example.deboardv2.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ControllerApiTest {

    private static final String TEST_APP_KEY =
            "4B398FBF0C39348DFD0E7188B151F3F734ACCD37006BCA207077FCA52701E3D5";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    UserService userService;

    @MockitoBean
    PostService postService;

    @MockitoBean
    SearchService searchService;

    @MockitoBean
    CommentsService commentsService;

    @MockitoBean
    FeedService feedService;

    private JwtTokenProvider jwtTokenProvider;
    private String validAccessToken;

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

        TokenBody tokenBody = new TokenBody(1L, "testUser", Role.ROLE_MEMBER);
        validAccessToken = jwtTokenProvider.issue(tokenBody, Duration.ofMinutes(30));
    }

    // ========== AuthController ==========

    @Test
    @DisplayName("POST /api/auth/signup - 닉네임 @NotBlank 위반 시 400 반환")
    void signup_닉네임_빈값_400() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("nickname", "");
        body.put("password", "validPass1");
        body.put("email", "valid@email.com");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/signup - 비밀번호 @NotBlank 위반 시 400 반환")
    void signup_비밀번호_빈값_400() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("nickname", "validNick");
        body.put("password", "");
        body.put("email", "valid@email.com");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/signup - 이메일 @NotBlank 위반 시 400 반환")
    void signup_이메일_빈값_400() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("nickname", "validNick");
        body.put("password", "validPass1");
        body.put("email", "");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/signup - 이메일 @Email 형식 위반 시 400 반환")
    void signup_이메일_형식위반_400() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("nickname", "validNick");
        body.put("password", "validPass1");
        body.put("email", "not-an-email");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/signup - 닉네임 @Size(min=2) 위반(1자) 시 400 반환")
    void signup_닉네임_1자_400() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("nickname", "a");
        body.put("password", "validPass1");
        body.put("email", "valid@email.com");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/signup - 비밀번호 @Size(min=8) 위반(7자) 시 400 반환")
    void signup_비밀번호_7자_400() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("nickname", "validNick");
        body.put("password", "1234567");
        body.put("email", "valid@email.com");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/signin - @Valid 위반(이메일 형식 불일치) 시 400 반환")
    void signin_유효성위반_400() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("email", "not-an-email");
        body.put("password", "");

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/signin - 정상 요청 시 200 + Set-Cookie accessToken/refreshToken 포함")
    void signin_정상요청_200_쿠키포함() throws Exception {
        TokenBody tokenBody = new TokenBody(1L, "testUser", Role.ROLE_MEMBER);
        String accessToken = jwtTokenProvider.issue(tokenBody, Duration.ofMinutes(30));
        String refreshToken = jwtTokenProvider.issue(tokenBody, Duration.ofHours(1));

        JwtToken jwtToken = new JwtToken(accessToken, refreshToken);
        LoginResponse loginResponse = new LoginResponse(jwtToken, null);

        given(authService.signIn(any(SignInRequest.class))).willReturn(loginResponse);

        Map<String, String> body = new HashMap<>();
        body.put("email", "test@email.com");
        body.put("password", "validPass1");

        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"));
    }

    // ========== PostController ==========

    @Test
    @DisplayName("POST /api/posts - 비인증 요청 시 401 반환")
    void createPost_비인증_401() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("title", "제목");
        body.put("content", "내용");

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/posts - 인증 + title @NotBlank 위반 시 400 반환")
    void createPost_인증_제목빈값_400() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("title", "");
        body.put("content", "내용");

        mockMvc.perform(post("/api/posts")
                        .cookie(new Cookie("accessToken", validAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/posts - 인증 + content @NotBlank 위반 시 400 반환")
    void createPost_인증_내용빈값_400() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("title", "제목");
        body.put("content", "");

        mockMvc.perform(post("/api/posts")
                        .cookie(new Cookie("accessToken", validAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/posts/{id} - 비인증 요청 시 401 반환")
    void updatePost_비인증_401() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("title", "수정 제목");
        body.put("content", "수정 내용");

        mockMvc.perform(put("/api/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/posts/{id} - 비인증 요청 시 401 반환")
    void deletePost_비인증_401() throws Exception {
        mockMvc.perform(delete("/api/posts/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/posts - 비인증 요청 시 200 반환 (공개 엔드포인트)")
    void getPosts_비인증_200() throws Exception {
        given(postService.readAll(10, 0)).willReturn(org.springframework.data.domain.Page.empty());

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk());
    }

    // ========== CommentsController ==========

    @Test
    @DisplayName("POST /api/comments - 비인증 요청 시 401 반환")
    void createComment_비인증_401() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("postId", 1);
        body.put("content", "댓글 내용");

        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/comments/{id} - 비인증 요청 시 401 반환")
    void deleteComment_비인증_401() throws Exception {
        mockMvc.perform(delete("/api/comments/1"))
                .andExpect(status().isUnauthorized());
    }

    // ========== FeedController ==========

    @Test
    @DisplayName("POST /api/rss/feed - 비인증 요청 시 401 반환")
    void addFeed_비인증_401() throws Exception {
        mockMvc.perform(post("/api/rss/feed")
                        .param("name", "테스트피드")
                        .param("url", "https://example.com/rss"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/rss/user-feed - 비인증 요청 시 401 반환")
    void addUserFeed_비인증_401() throws Exception {
        mockMvc.perform(post("/api/rss/user-feed")
                        .param("name", "내피드")
                        .param("url", "https://example.com/rss"))
                .andExpect(status().isUnauthorized());
    }
}
