package org.example.deboardv2.likes.service;

import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.likes.repository.LikesRepository;
import org.example.deboardv2.post.dto.PostCreateRequest;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.user.dto.SignupRequest;
import org.example.deboardv2.user.dto.TokenBody;
import org.example.deboardv2.user.entity.Role;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

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
        var lockKeys = redisTemplate.keys("lock:likes:post:*");
        if (lockKeys != null && !lockKeys.isEmpty()) {
            redisTemplate.delete(lockKeys);
        }
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
        PostCreateRequest postCreateDto = new PostCreateRequest("동시성 테스트 게시글", "내용");
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

        log.info("[100명 동시] likeEntity 수: {}, likeCount: {}", actualLikesCount, likeCount);
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

        PostCreateRequest postCreateDto = new PostCreateRequest("중복 좋아요 테스트", "내용");
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
        Post updatedPost = postRepository.findById(postId).orElseThrow();
        int actualLikesCount = likesRepository.countByPostId(postId);
        int likeCount = updatedPost.getLikeCount();
        log.info("[동일 사용자 100회] likeEntity 수: {}, likeCount: {}", actualLikesCount, likeCount);
        assertThat(actualLikesCount).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("[비교] dirty checking 방식 — 100명 동시 좋아요 시 likeEntity는 100개지만 likeCount는 100 미만 (lost update)")
    void dirty_checking_lost_update_발생() throws InterruptedException {
        // given: 100명의 유저 생성
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            SignupRequest req = new SignupRequest();
            req.setNickname("compare_user_" + i);
            req.setEmail("compare" + i + "@example.com");
            req.setPassword("password123");
            users.add(userRepository.save(User.toEntity(req)));
        }

        PostCreateRequest postCreateDto = new PostCreateRequest("lost update 비교 테스트", "내용");
        Post post = postRepository.save(Post.from(postCreateDto, users.get(0)));
        Long postId = post.getId();

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // when: 원본 코드 패턴 재현 — likes INSERT + dirty checking으로 likeCount 업데이트
        for (int i = 0; i < threadCount; i++) {
            final User currentUser = users.get(i);
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    // 원본 코드 흐름: likes 저장 + post를 읽어서 count로 덮어씀
                    Post postRef = postRepository.getReferenceById(postId);
                    likesRepository.save(org.example.deboardv2.likes.entity.Likes.toEntity(currentUser, postRef));

                    // dirty checking 패턴: 현재 값을 읽어서 +1 후 저장 → 동시에 읽으면 lost update
                    Post current = postRepository.findById(postId).orElseThrow();
                    current.increaseLikeCount();
                    postRepository.save(current);
                } catch (Exception e) {
                    log.warn("예외 발생: {}", e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        // then: likeEntity는 100개지만 likeCount는 100 미만 → 불일치
        Post updatedPost = postRepository.findById(postId).orElseThrow();
        int likeCount = updatedPost.getLikeCount();
        int actualLikesCount = likesRepository.countByPostId(postId);
        log.info("[dirty checking] likeEntity 수: {}, likeCount: {} → 불일치: {}",
                actualLikesCount, likeCount, actualLikesCount != likeCount);
        assertThat(actualLikesCount).isEqualTo(100);       // likes 행은 정상 100개
        assertThat(likeCount).isLessThan(actualLikesCount); // likeCount는 더 적음
    }

    // ─────────────────────────────────────────────────────────────
    // 성능 비교 테스트 — @Modifying / 비관적 락 / 낙관적 락 / synchronized
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("[성능 비교] @Modifying 원자적 업데이트 — 100명 동시 처리시간 측정")
    void 성능비교_Modifying_처리시간() throws InterruptedException {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            SignupRequest req = new SignupRequest();
            req.setNickname("mod_user_" + i);
            req.setEmail("mod" + i + "@example.com");
            req.setPassword("password123");
            users.add(userRepository.save(User.toEntity(req)));
        }
        PostCreateRequest postCreateDto = new PostCreateRequest("성능비교 @Modifying", "내용");
        Post post = postRepository.save(Post.from(postCreateDto, users.get(0)));
        Long postId = post.getId();

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final Long userId = users.get(i).getId();
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    txTemplate.execute(status -> {
                        postRepository.increaseLikeCount(postId);
                        Post postRef = postRepository.getReferenceById(postId);
                        User userRef = userRepository.getReferenceById(userId);
                        likesRepository.save(org.example.deboardv2.likes.entity.Likes.toEntity(userRef, postRef));
                        return null;
                    });
                } catch (Exception e) {
                    log.warn("[@Modifying] 예외: {}", e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long start = System.currentTimeMillis();
        startLatch.countDown();
        doneLatch.await();
        long elapsed = System.currentTimeMillis() - start;
        executorService.shutdown();

        Post updatedPost = postRepository.findById(postId).orElseThrow();
        int actualLikesCount = likesRepository.countByPostId(postId);
        log.info("[성능 비교] @Modifying: {}ms | likeEntity={}, likeCount={}", elapsed, actualLikesCount, updatedPost.getLikeCount());
        assertThat(updatedPost.getLikeCount()).isEqualTo(actualLikesCount);
    }

    @Test
    @DisplayName("[성능 비교] 비관적 락 (SELECT FOR UPDATE) — 100명 동시 처리시간 측정")
    void 성능비교_비관적락_처리시간() throws InterruptedException {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            SignupRequest req = new SignupRequest();
            req.setNickname("pess_user_" + i);
            req.setEmail("pess" + i + "@example.com");
            req.setPassword("password123");
            users.add(userRepository.save(User.toEntity(req)));
        }
        PostCreateRequest postCreateDto = new PostCreateRequest("성능비교 비관적락", "내용");
        Post post = postRepository.save(Post.from(postCreateDto, users.get(0)));
        Long postId = post.getId();

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final Long userId = users.get(i).getId();
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    txTemplate.execute(status -> {
                        // SELECT FOR UPDATE → 트랜잭션 시작부터 커밋까지 exclusive lock 보유
                        Post postLocked = postRepository.findByIdForUpdate(postId).orElseThrow();
                        postLocked.increaseLikeCount();
                        User userRef = userRepository.getReferenceById(userId);
                        likesRepository.save(org.example.deboardv2.likes.entity.Likes.toEntity(userRef, postLocked));
                        return null; // commit 시 dirty checking → UPDATE post
                    });
                } catch (Exception e) {
                    log.warn("[비관적락] 예외: {}", e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long start = System.currentTimeMillis();
        startLatch.countDown();
        doneLatch.await();
        long elapsed = System.currentTimeMillis() - start;
        executorService.shutdown();

        Post updatedPost = postRepository.findById(postId).orElseThrow();
        int actualLikesCount = likesRepository.countByPostId(postId);
        log.info("[성능 비교] 비관적 락: {}ms | likeEntity={}, likeCount={}", elapsed, actualLikesCount, updatedPost.getLikeCount());
        assertThat(updatedPost.getLikeCount()).isEqualTo(actualLikesCount);
    }

    @Test
    @Disabled("Post에 @Version 미적용 — RSS/조회수 등 다른 연산과의 version 충돌 방지를 위해 의도적으로 제외")
    @DisplayName("[성능 비교] 낙관적 락 (버전 충돌 시 재시도) — 100명 동시 처리시간 측정")
    void 성능비교_낙관적락_처리시간() throws InterruptedException {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            SignupRequest req = new SignupRequest();
            req.setNickname("opt_user_" + i);
            req.setEmail("opt" + i + "@example.com");
            req.setPassword("password123");
            users.add(userRepository.save(User.toEntity(req)));
        }
        PostCreateRequest postCreateDto = new PostCreateRequest("성능비교 낙관적락", "내용");
        Post post = postRepository.save(Post.from(postCreateDto, users.get(0)));
        Long postId = post.getId();

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final Long userId = users.get(i).getId();
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    int maxRetries = 30;
                    for (int retry = 0; retry < maxRetries; retry++) {
                        try {
                            final int currentRetry = retry;
                            txTemplate.execute(status -> {
                                // findById → @Version 포함한 엔티티 로드
                                Post p = postRepository.findById(postId).orElseThrow();
                                p.increaseLikeCount();
                                User userRef = userRepository.getReferenceById(userId);
                                likesRepository.save(org.example.deboardv2.likes.entity.Likes.toEntity(userRef, p));
                                return null;
                                // commit 시: UPDATE post SET like_count=?, version=version+1 WHERE id=? AND version=?
                                // → 다른 TX가 먼저 커밋하면 version 불일치 → ObjectOptimisticLockingFailureException
                            });
                            break; // 성공 시 루프 탈출
                        } catch (ObjectOptimisticLockingFailureException e) {
                            if (retry >= maxRetries - 1) {
                                log.warn("[낙관적락] 최대 재시도 초과: userId={}", userId);
                            } else {
                                Thread.sleep(10L * (retry + 1)); // 지수 백오프
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("[낙관적락] 예외: {}", e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long start = System.currentTimeMillis();
        startLatch.countDown();
        doneLatch.await();
        long elapsed = System.currentTimeMillis() - start;
        executorService.shutdown();

        Post updatedPost = postRepository.findById(postId).orElseThrow();
        int actualLikesCount = likesRepository.countByPostId(postId);
        log.info("[성능 비교] 낙관적 락: {}ms | likeEntity={}, likeCount={}", elapsed, actualLikesCount, updatedPost.getLikeCount());
        assertThat(updatedPost.getLikeCount()).isEqualTo(actualLikesCount);
    }

    @Test
    @DisplayName("[성능 비교] synchronized — 100명 동시 처리시간 측정 (단일 JVM에서만 유효)")
    void 성능비교_synchronized_처리시간() throws InterruptedException {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            SignupRequest req = new SignupRequest();
            req.setNickname("sync_user_" + i);
            req.setEmail("sync" + i + "@example.com");
            req.setPassword("password123");
            users.add(userRepository.save(User.toEntity(req)));
        }
        PostCreateRequest postCreateDto = new PostCreateRequest("성능비교 synchronized", "내용");
        Post post = postRepository.save(Post.from(postCreateDto, users.get(0)));
        Long postId = post.getId();

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        Object lock = new Object(); // JVM 모니터 락 (단일 인스턴스 공유)
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final Long userId = users.get(i).getId();
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    // JVM 레벨 직렬화 — 서버가 2대 이상이면 각 JVM이 별도 lock 객체를 가져 무효
                    synchronized (lock) {
                        txTemplate.execute(status -> {
                            Post p = postRepository.findById(postId).orElseThrow();
                            p.increaseLikeCount();
                            User userRef = userRepository.getReferenceById(userId);
                            likesRepository.save(org.example.deboardv2.likes.entity.Likes.toEntity(userRef, p));
                            return null;
                        });
                    }
                } catch (Exception e) {
                    log.warn("[synchronized] 예외: {}", e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long start = System.currentTimeMillis();
        startLatch.countDown();
        doneLatch.await();
        long elapsed = System.currentTimeMillis() - start;
        executorService.shutdown();

        Post updatedPost = postRepository.findById(postId).orElseThrow();
        int actualLikesCount = likesRepository.countByPostId(postId);
        log.info("[성능 비교] synchronized: {}ms | likeEntity={}, likeCount={}", elapsed, actualLikesCount, updatedPost.getLikeCount());
        // 단일 JVM에서는 정합성 보장 — 스케일아웃 시 무효
        assertThat(updatedPost.getLikeCount()).isEqualTo(actualLikesCount);
    }

    @Test
    @DisplayName("[성능 비교] Redis 분산락 (SETNX) — 100명 동시 처리시간 측정")
    void 성능비교_Redis분산락_처리시간() throws InterruptedException {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            SignupRequest req = new SignupRequest();
            req.setNickname("redis_user_" + i);
            req.setEmail("redis" + i + "@example.com");
            req.setPassword("password123");
            users.add(userRepository.save(User.toEntity(req)));
        }
        PostCreateRequest postCreateDto = new PostCreateRequest("성능비교 Redis분산락", "내용");
        Post post = postRepository.save(Post.from(postCreateDto, users.get(0)));
        Long postId = post.getId();
        String lockKey = "lock:likes:post:" + postId;

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final Long userId = users.get(i).getId();
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    // spin-wait: SETNX 성공할 때까지 재시도 (TTL 5초로 데드락 방지)
                    while (!Boolean.TRUE.equals(
                            redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(5)))) {
                        Thread.sleep(10);
                    }
                    try {
                        txTemplate.execute(status -> {
                            // 락이 직렬화를 보장하므로 dirty checking도 lost update 없이 정합
                            Post p = postRepository.findById(postId).orElseThrow();
                            p.increaseLikeCount();
                            User userRef = userRepository.getReferenceById(userId);
                            likesRepository.save(org.example.deboardv2.likes.entity.Likes.toEntity(userRef, p));
                            return null;
                        });
                    } finally {
                        redisTemplate.delete(lockKey); // 반드시 해제
                    }
                } catch (Exception e) {
                    log.warn("[Redis분산락] 예외: {}", e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long start = System.currentTimeMillis();
        startLatch.countDown();
        doneLatch.await();
        long elapsed = System.currentTimeMillis() - start;
        executorService.shutdown();

        Post updatedPost = postRepository.findById(postId).orElseThrow();
        int actualLikesCount = likesRepository.countByPostId(postId);
        log.info("[성능 비교] Redis 분산락: {}ms | likeEntity={}, likeCount={}", elapsed, actualLikesCount, updatedPost.getLikeCount());
        assertThat(updatedPost.getLikeCount()).isEqualTo(actualLikesCount);
        assertThat(actualLikesCount).isEqualTo(100);
    }

    @Test
    @DisplayName("[성능 비교] Redisson 분산락 (pub/sub 방식) — 100명 동시 처리시간 측정")
    void 성능비교_Redisson분산락_처리시간() throws InterruptedException {
        // Redisson 클라이언트 직접 생성 (Spring Boot starter 없이 테스트 전용)
        Config config = new Config();
        config.useSingleServer().setAddress("redis://localhost:6379");
        RedissonClient redissonClient = Redisson.create(config);

        try {
            List<User> users = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                SignupRequest req = new SignupRequest();
                req.setNickname("redisson_user_" + i);
                req.setEmail("redisson" + i + "@example.com");
                req.setPassword("password123");
                users.add(userRepository.save(User.toEntity(req)));
            }
            PostCreateRequest postCreateDto = new PostCreateRequest("성능비교 Redisson분산락", "내용");
            Post post = postRepository.save(Post.from(postCreateDto, users.get(0)));
            Long postId = post.getId();

            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            int threadCount = 100;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final Long userId = users.get(i).getId();
                executorService.submit(() -> {
                    try {
                        startLatch.await();
                        RLock lock = redissonClient.getLock("rlock:likes:post:" + postId);
                        // pub/sub 기반 blocking wait — 폴링 없이 락 해제 이벤트를 구독해서 즉시 반응
                        lock.lock();
                        try {
                            txTemplate.execute(status -> {
                                Post p = postRepository.findById(postId).orElseThrow();
                                p.increaseLikeCount();
                                User userRef = userRepository.getReferenceById(userId);
                                likesRepository.save(org.example.deboardv2.likes.entity.Likes.toEntity(userRef, p));
                                return null;
                            });
                        } finally {
                            lock.unlock();
                        }
                    } catch (Exception e) {
                        log.warn("[Redisson] 예외: {}", e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            long start = System.currentTimeMillis();
            startLatch.countDown();
            doneLatch.await();
            long elapsed = System.currentTimeMillis() - start;
            executorService.shutdown();

            Post updatedPost = postRepository.findById(postId).orElseThrow();
            int actualLikesCount = likesRepository.countByPostId(postId);
            log.info("[성능 비교] Redisson 분산락: {}ms | likeEntity={}, likeCount={}", elapsed, actualLikesCount, updatedPost.getLikeCount());
            assertThat(updatedPost.getLikeCount()).isEqualTo(actualLikesCount);
            assertThat(actualLikesCount).isEqualTo(100);
        } finally {
            redissonClient.shutdown();
        }
    }
}
