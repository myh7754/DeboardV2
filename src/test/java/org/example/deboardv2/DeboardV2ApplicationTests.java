package org.example.deboardv2;

import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.fixture.UserFixture;
import org.example.deboardv2.likes.service.LikeService;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.post.service.PostService;
import org.example.deboardv2.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@Transactional
@Slf4j
class DeboardV2ApplicationTests {
    public List<User> testUsers;
    public Post post;
    @Autowired
    private LikeService likeService;
    @Autowired
    private PostService postService;
    @Autowired
    private PostRepository postRepository;

    @BeforeEach
    void setUp() {
        // 테스트용 사용자 생성 100명
        testUsers = UserFixture.create100Users();

        //
        post = new Post();
        post.test();
        postRepository.save(post);
    }

    @Test
    public void 좋아요_동시성_문제_Test() throws InterruptedException {
        int threadCount = 2;
        // 스레드 풀 생성
        // 동시에 여러 작업을 수행할 때 쓰임
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < 2; i++) {
            final int userIndex = i;
            executorService.submit(()-> {
                try {
                    likeService.toggleLike(post.getId(), testUsers.get(userIndex).getId());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기 (최대 10초)
        boolean finished = latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        Post postById = postService.getPostById(post.getId());
        log.info("Post 테이블 레코드 수 {}", postById.getLikeCount());

        int likeCount = likeService.getLikeCount(post.getId());
        log.info("likes 테이블 레코드 수 {}", likeCount);



    }

}
