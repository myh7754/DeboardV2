package org.example.deboardv2.likes.service;

import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.likes.repository.LikesRepository;
import org.example.deboardv2.post.dto.PostCreateDto;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.user.dto.SignupRequest;
import org.example.deboardv2.user.dto.TokenBody;
import org.example.deboardv2.user.entity.Role;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class LikesConcurrencyTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private LikesRepository likesRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        likesRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        likesRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("100명의 서로 다른 사용자가 동시에 toggleLike → likeCount가 실제 Likes 행 수와 일치")
    void 서로다른_100명_동시_toggleLike_likeCount_일치() throws InterruptedException {
        // given: 100명의 유저 생성
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            SignupRequest req = new SignupRequest();
            req.setNickname("user_" + i);
            req.setEmail("user" + i + "@example.com");
            req.setPassword("password123");
            users.add(userRepository.save(User.toEntity(req)));
        }

        // 게시글 1개 생성 (첫 번째 유저를 작성자로 사용)
        PostCreateDto postCreateDto = new PostCreateDto("동시성 테스트 게시글", "내용");
        Post post = postRepository.save(Post.from(postCreateDto, users.get(0)));
        Long postId = post.getId();

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // when: 100개 스레드가 동시에 toggleLike 호출
        for (int i = 0; i < threadCount; i++) {
            final Long userId = users.get(i).getId();
            final int idx = i;
            executorService.submit(() -> {
                try {
                    startLatch.await(); // 동시 시작 대기
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    new TokenBody(userId, "user_" + idx, Role.ROLE_MEMBER),
                                    null
                            )
                    );
                    SecurityContextHolder.setContext(context);
                    likeService.toggleLike(postId);
                } catch (Exception e) {
                    log.warn("toggleLike 예외 발생 (무시): {}", e.getMessage());
                } finally {
                    SecurityContextHolder.clearContext();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 동시 시작 신호
        doneLatch.await();      // 모든 스레드 완료 대기
        executorService.shutdown();

        // then: likeCount와 실제 Likes 행 수 비교
        Post updatedPost = postRepository.findById(postId).orElseThrow();
        int actualLikesCount = likesRepository.countByPostId(postId);
        int likeCount = updatedPost.getLikeCount();

        log.info("likeCount = {}, actualLikesCount = {}", likeCount, actualLikesCount);
        assertThat(likeCount).isEqualTo(actualLikesCount);
    }

    @Test
    @DisplayName("같은 사용자 100개 동시 요청 → Likes 레코드 최대 1개, DataIntegrityViolationException 무시 처리 확인")
    void 동일_사용자_100개_동시_toggleLike_최대1개() throws InterruptedException {
        // given: 유저 1명, 게시글 1개
        SignupRequest req = new SignupRequest();
        req.setNickname("single_user");
        req.setEmail("single@example.com");
        req.setPassword("password123");
        User user = userRepository.save(User.toEntity(req));

        PostCreateDto postCreateDto = new PostCreateDto("중복 좋아요 테스트", "내용");
        Post post = postRepository.save(Post.from(postCreateDto, user));
        Long postId = post.getId();
        Long userId = user.getId();

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // when: 100개 스레드가 동일한 userId로 동시에 toggleLike 호출
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    new TokenBody(userId, "single_user", Role.ROLE_MEMBER),
                                    null
                            )
                    );
                    SecurityContextHolder.setContext(context);
                    likeService.toggleLike(postId);
                } catch (Exception e) {
                    // DataIntegrityViolationException을 포함한 모든 예외는
                    // LikesServiceImpl 내부에서 이미 catch 처리됨
                    // 혹시 외부로 나온 예외도 무시
                    log.warn("toggleLike 예외 발생 (무시): {}", e.getMessage());
                } finally {
                    SecurityContextHolder.clearContext();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        // then: Likes 레코드는 최대 1개 (0 또는 1)
        int actualLikesCount = likesRepository.countByPostId(postId);
        log.info("동일 유저 100회 동시 요청 후 Likes 행 수: {}", actualLikesCount);
        assertThat(actualLikesCount).isLessThanOrEqualTo(1);
    }
}
