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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.BDDMockito.given;


@SpringBootTest
@Transactional
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
    @MockitoBean
    private UserService userService; // 실제 JWT 인증 우회

    private Post post;
    private static final int THREAD_COUNT = 50;

    @BeforeEach
    void setup() {
        // 테스트용 유저 50명 생성
        for (int i = 1; i <= THREAD_COUNT; i++) {
            MemberDetails details = MemberDetails.builder()
                    .name("userA" + i)
                    .email("userA" + i + "@test.com")
                    .provider("GOOGLE")
                    .attributes(null)
                    .build();

            User user = User.builder()
                    .memberDetails(details)
                    .build();

            userRepository.save(user);
        }

        // 테스트용 게시글
        User author = userRepository.findById(1L).orElseThrow();
        post = new Post();
        post.test(); // id = 1000L 등 임의 설정
        postRepository.save(post);
    }

    @Test
    public void concurrentLikesDifferentUsersTest() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 1; i <= THREAD_COUNT; i++) {
            final long userId = i;
            executor.submit(() -> {
                try {
                    // 각 스레드마다 다른 사용자
                    User user = userRepository.findById(userId).orElseThrow();

                    given(userService.getCurrentUser()).willReturn(user);
                    // 실제 LikeService 호출 시 현재 유저로 토글
                    likeService.toggleLike(post.getId());

                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        long likeCount = likesRepository.countByPostId(post.getId());
        Post updatedPost = postRepository.findById(post.getId()).orElseThrow();

        System.out.println("Likes 테이블 개수 = " + likeCount);
        System.out.println("Post.likeCount = " + updatedPost.getLikeCount());
    }


}


