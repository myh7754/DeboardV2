package org.example.deboardv2;

import lombok.extern.slf4j.Slf4j;

import org.example.deboardv2.likes.repository.LikesRepository;
import org.example.deboardv2.likes.service.LikeService;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.user.dto.MemberDetails;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.repository.UserRepository;
import org.example.deboardv2.user.service.UserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;



@SpringBootTest
@Slf4j
public class DeboardV2ApplicationTests {
    @Autowired
    private LikeService likeService;

    @Autowired
    private LikesRepository likesRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    private Post post;
    private List<User> testUsers;
    private static final int THREAD_COUNT = 100;

    @BeforeEach
    void setup() {
        // 기존 데이터 정리 (중요!)
        likesRepository.deleteAll();

        testUsers = new ArrayList<>();

        // 테스트용 유저 100명 생성 및 저장
        for (int i = 1; i <= THREAD_COUNT; i++) {
            MemberDetails details = MemberDetails.builder()
                    .name("userooff" + i)
                    .email("userooff" + i + "@test.com")
                    .provider("GOOGLE")
                    .attributes(null)
                    .build();

            User user = User.builder()
                    .memberDetails(details)
                    .build();

            testUsers.add(userRepository.save(user));
        }

        // 테스트용 게시글
        post = new Post();
        post.test();
        post = postRepository.save(post);

        log.info("===== 테스트 준비 완료 =====");
        log.info("생성된 Post ID: {}", post.getId());
        log.info("생성된 User 수: {}", testUsers.size());
    }

    @Test
    void 좋아요_동시성_문제_테스트() throws InterruptedException {
        int threadCount = THREAD_COUNT;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        for (int i = 0; i < THREAD_COUNT; i++) {
            int userIndex = i;
            executorService.submit(() -> {
                try {
                    User user =  testUsers.get(userIndex);
                    boolean likesStatus = likeService.toggleLikeRecord(post.getId(), user);
                    likeService.updateLikeCount(post.getId(), likesStatus);
                } catch (Exception e) {
                    log.error(e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(); // 모든 스레드 종료 대기
        executorService.shutdown();

        // === 결과 검증 ===
        Post result = postRepository.findById(post.getId()).orElseThrow();
        long likesCount = likesRepository.countByPostId(post.getId());

        log.info("최종 likeCount = {}", result.getLikeCount());
        log.info("Likes 테이블 count = {}", likesCount);

        // 둘이 일치해야 함
        Assertions.assertEquals(likesCount, result.getLikeCount(),
                String.format("likeCount 불일치! post=%d, likesTable=%d", result.getLikeCount(), likesCount));
    }
}


