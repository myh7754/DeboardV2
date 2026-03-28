package org.example.deboardv2.likes.service;

import jakarta.persistence.EntityManager;
import org.example.deboardv2.likes.repository.LikesRepository;
import org.example.deboardv2.post.dto.PostCreateRequest;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.user.dto.SignupRequest;
import org.example.deboardv2.user.dto.TokenBody;
import org.example.deboardv2.user.entity.Role;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LikesServiceIntegrationTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private LikesRepository likesRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User testUser;
    private Post testPost;

    @BeforeEach
    void setUp() {
        likesRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();

        SignupRequest signupRequest = new SignupRequest();
        signupRequest.setNickname("testuser");
        signupRequest.setEmail("testuser@example.com");
        signupRequest.setPassword("password123");
        testUser = userRepository.save(User.toEntity(signupRequest));

        PostCreateRequest postCreateDto = new PostCreateRequest("테스트 게시글", "테스트 내용");
        testPost = postRepository.save(Post.from(postCreateDto, testUser));
    }

    private void setSecurityContext(Long userId) {
        TokenBody tokenBody = new TokenBody(userId, "testuser", Role.ROLE_MEMBER);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(tokenBody, null)
        );
        SecurityContextHolder.setContext(context);
    }

    private void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("toggleLike - 좋아요 없을 때 Likes 추가 및 likeCount +1")
    void toggleLike_좋아요없을때_추가및likeCount증가() {
        // given
        setSecurityContext(testUser.getId());
        Long postId = testPost.getId();

        // when
        likeService.toggleLike(postId);

        // then: @Modifying JPQL은 1차 캐시를 우회하므로 flush/clear 후 재조회
        entityManager.flush();
        entityManager.clear();
        Post updatedPost = postRepository.findById(postId).orElseThrow();
        assertThat(updatedPost.getLikeCount()).isEqualTo(1);
        assertThat(likesRepository.existsByPostIdAndUserId(postId, testUser.getId())).isTrue();

        clearSecurityContext();
    }

    @Test
    @DisplayName("toggleLike - 좋아요 있을 때 Likes 삭제 및 likeCount -1")
    void toggleLike_좋아요있을때_삭제및likeCount감소() {
        // given
        setSecurityContext(testUser.getId());
        Long postId = testPost.getId();
        likeService.toggleLike(postId); // 좋아요 추가
        entityManager.flush();
        entityManager.clear();

        // when
        likeService.toggleLike(postId); // 좋아요 취소

        // then
        entityManager.flush();
        entityManager.clear();
        Post updatedPost = postRepository.findById(postId).orElseThrow();
        assertThat(updatedPost.getLikeCount()).isEqualTo(0);
        assertThat(likesRepository.existsByPostIdAndUserId(postId, testUser.getId())).isFalse();

        clearSecurityContext();
    }

    @Test
    @DisplayName("getLikeStatus - 비인증 사용자는 false 반환")
    void getLikeStatus_비인증사용자_false() {
        // given: SecurityContext 초기화 상태 (익명 사용자 없이 빈 컨텍스트)
        SecurityContextHolder.clearContext();
        Long postId = testPost.getId();

        // when
        boolean status = likeService.getLikeStatus(postId);

        // then
        assertThat(status).isFalse();
    }

    @Test
    @DisplayName("getLikeStatus - 좋아요한 게시글은 true 반환")
    void getLikeStatus_좋아요한게시글_true() {
        // given
        setSecurityContext(testUser.getId());
        Long postId = testPost.getId();
        likeService.toggleLike(postId); // 좋아요 추가

        // when
        boolean status = likeService.getLikeStatus(postId);

        // then
        assertThat(status).isTrue();

        clearSecurityContext();
    }

    @Test
    @DisplayName("getLikeStatus - 좋아요 안 한 게시글은 false 반환")
    void getLikeStatus_좋아요안한게시글_false() {
        // given
        setSecurityContext(testUser.getId());
        Long postId = testPost.getId();
        // toggleLike 호출 안 함

        // when
        boolean status = likeService.getLikeStatus(postId);

        // then
        assertThat(status).isFalse();

        clearSecurityContext();
    }
}
