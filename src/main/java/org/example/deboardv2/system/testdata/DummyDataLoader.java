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
import org.example.deboardv2.user.dto.SignupRequest;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;

//    @Override
//    @Transactional // Batch Insert 시에는 트랜잭션 단위로 묶는게 중요
//    public void run(String... args) throws Exception {
//        int total = 100_000;
//        int batchSize = 1000;
//        log.info("더미 데이터 100만 건 삽입 시작...");
//
//        // user
//        for (int i = 1; i <= total; i++) {
//            SignupRequest signupRequest = new SignupRequest();
//            signupRequest.setNickname("user" + i);
//            signupRequest.setEmail("user" + i + "@gmail.com");
//            signupRequest.setPassword(passwordEncoder.encode("password" + i));
//
//            User user = User.toEntity(signupRequest);
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
//    }

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
//        int total = 100_000;
//        int batchSize = 1000;
//
//        log.info("더미 데이터 {}건 삽입 시작...", total);
//        String password123 = passwordEncoder.encode("password123");
//        List<Object[]> userBatch = new ArrayList<>();
//        for (int i = 1; i <= total; i++) {
//            userBatch.add(new Object[]{
//                    "user" + i + "@gmail.com",         // email (첫 번째)
//                    "user" + i,                        // nickname (두 번째)
//                    password123,                       // password (세 번째)
//                    null,                              // provider (네 번째) - NULL 허용
//                    "ROLE_MEMBER"                      // role (다섯 번째)
//            });
//
//            if (i % batchSize == 0) {
//                jdbcTemplate.batchUpdate(
//                        "INSERT INTO user (email, nickname, password, provider, role) VALUES (?, ?, ?, ?, ?)",
//                        userBatch
//                );
//                userBatch.clear();
//                log.info("{} users inserted...", i);
//            }
//        }
//
//        if (!userBatch.isEmpty()) {
//            jdbcTemplate.batchUpdate(
//                    "INSERT INTO user (email, nickname, password, provider, role) VALUES (?, ?, ?, ?, ?)",
//                    userBatch
//            );
//        }
//
//        log.info("user 데이터 삽입 완료!");
//
//        // Post 데이터도 동일한 방식으로 batch insert
//        List<Object[]> postBatch = new ArrayList<>();
//        LocalDateTime now = LocalDateTime.now();
//        for (int i = 1; i <= total; i++) {
//            postBatch.add(new Object[]{
//                    0,                                  // like_count (NOT NULL)
//                    now,                                // created_at (NOT NULL)
//                    null,                               // updated_at (NULL 허용)
//                    1L,                                 // user_id
//                    "content" + i,                      // content
//                    null,                               // image (NULL 허용)
//                    "title" + i                         // title
//            });
//
//            if (i % batchSize == 0) {
//                jdbcTemplate.batchUpdate(
//                        "INSERT INTO post (like_count, created_at, updated_at, user_id, content, image, title) VALUES (?, ?, ?, ?, ?, ?, ?)",
//                        postBatch
//                );
//                postBatch.clear();
//                log.info("{} posts inserted...", i);
//            }
//        }
//
//        if (!postBatch.isEmpty()) {
//            jdbcTemplate.batchUpdate(
//                    "INSERT INTO post (like_count, created_at, updated_at, user_id, content, image, title) VALUES (?, ?, ?, ?, ?, ?, ?)",
//                    postBatch
//            );
//        }
//
//        log.info("post 데이터 삽입 완료!");
    }

}
