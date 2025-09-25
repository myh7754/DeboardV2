package org.example.deboardv2.system.testdata;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.comment.repository.CommentsRepository;
import org.example.deboardv2.likes.repository.LikesRepository;
import org.example.deboardv2.post.dto.PostCreateDto;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.user.dto.MemberDetails;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


@Component
@RequiredArgsConstructor
@Slf4j
public class DummyDataLoader implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final LikesRepository likesRepository;
    private final CommentsRepository commentsRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional // Batch Insert 시에는 트랜잭션 단위로 묶는게 중요
    public void run(String... args) throws Exception {
        int total = 100_000;
        int batchSize = 1000;
        log.info("더미 데이터 100만 건 삽입 시작...");

//        // user
//        for (int i = 1; i <= total; i++) {
//            User user = User.builder()
//                    .memberDetails(new MemberDetails(
//                            "user" + i,
//                            "user" + i + "@example.com",
//                            "GOOGLE",
//                            null
//                    ))
//                    .build();
//
//            entityManager.persist(user); // saveAll 대신 persist 사용
//
//            // Batch 단위마다 flush + clear
//            if (i % batchSize == 0) {
//                entityManager.flush();
//                entityManager.clear();
//                log.info("{} users inserted...", i);
//            }
//        }
//
//        // 남은 데이터 flush
//        entityManager.flush();
//        entityManager.clear();
//        log.info("user 데이터 삽입 완료!");
//
//        // post
//        for (int i = 1; i <= total; i++) {
//            User user = entityManager.getReference(User.class, (Long) 1L);
//            PostCreateDto postCreateDto = new PostCreateDto();
//            postCreateDto.setContent("content" + i);
//            postCreateDto.setTitle("title" + i);
//            Post post = Post.from(postCreateDto, user);
//            entityManager.persist(post);
//
//            if (i % batchSize == 0) {
//                entityManager.flush();
//                entityManager.clear();
//                log.info("{} posts inserted...", i);
//            }
//        }
//
//        // 남은 데이터 flush
//        entityManager.flush();
//        entityManager.clear();
//        log.info("post 데이터 삽입 완료!");
////
    }

}
