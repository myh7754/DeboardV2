package org.example.deboardv2;

import lombok.extern.slf4j.Slf4j;

import org.example.deboardv2.likes.repository.LikesRepository;
import org.example.deboardv2.likes.service.LikeService;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.user.dto.MemberDetails;
import org.example.deboardv2.user.dto.TokenBody;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.repository.UserRepository;
import org.example.deboardv2.user.service.UserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
    private static final int THREAD_COUNT = 120;

    @BeforeEach
    void setup() {
//        // 기존 데이터 정리 (중요!)
//        likesRepository.deleteAll();
//
//        testUsers = new ArrayList<>();
//
//        // 테스트용 유저 100명 생성 및 저장
//        for (int i = 1; i <= THREAD_COUNT; i++) {
//            MemberDetails details = MemberDetails.builder()
//                    .name("userooff" + i)
//                    .email("userooff" + i + "@test.com")
//                    .provider("GOOGLE")
//                    .attributes(null)
//                    .build();
//
//            User user = User.builder()
//                    .memberDetails(details)
//                    .build();
//
//            testUsers.add(userRepository.save(user));
//        }
//
//        // 테스트용 게시글
//        post = new Post();
//        post.test();
//        post = postRepository.save(post);
//
//        log.info("===== 테스트 준비 완료 =====");
//        log.info("생성된 Post ID: {}", post.getId());
//        log.info("생성된 User 수: {}", testUsers.size());
    }

    @Test
    void 좋아요_동시성_문제_테스트() throws InterruptedException {
        int threadCount = THREAD_COUNT;
        Long postId = 1L;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService executorService = Executors.newFixedThreadPool(300);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        // 개별 실행 시간의 합을 저장할 변수
        AtomicLong totalExecutionTime = new AtomicLong(0);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>()); // 각 요청별 소요시간 저장
        // 전체 시작 시간 측정
        long testStartTime = System.nanoTime();
        for (int i = 0; i < threadCount; i++) {
            Long userId = (long) (i + 1); // 1~300번 유저
            executorService.submit(() -> {
                try {
                    TokenBody tokenBody = new TokenBody(userId, null, null);
                    Authentication auth = new UsernamePasswordAuthenticationToken(tokenBody, null,
                            null);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    barrier.await();

                    long requestStart = System.nanoTime();
                    likeService.toggleLike(postId);
                    long requestEnd = System.nanoTime();
                    long duration =(requestEnd - requestStart)/1_000_000;
                    latencies.add(duration);
                    totalExecutionTime.addAndGet(duration);
                } catch (Exception e) {
                    log.error("에러발생 : "+ e.getMessage());
                } finally {
                    SecurityContextHolder.clearContext();
                    latch.countDown();
                }
            });
        }

        latch.await();
        long testEndTime = System.nanoTime();
        // 2. 결과 분석 및 출력
        double totalTimeSec = (testEndTime - testStartTime) / 1_000_000_000.0;
        double avgLatency = (double) totalExecutionTime.get() / threadCount;

        // TPS(초당 처리량) 계산
        double tps = threadCount / totalTimeSec;

        Post post = postRepository.findById(postId).orElseThrow();
        log.info("최종 좋아요 수 {}", post.getLikeCount());
        log.info("평균 응답 시간: {}ms", String.format("%.2f", avgLatency));
        log.info("초당 처리량(TPS): {}", String.format("%.2f", tps));
        assertThat(post.getLikeCount()).isEqualTo(likesRepository.countByPostId(postId));

    }
}


