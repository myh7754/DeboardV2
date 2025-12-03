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

        // EC2 배포용 시작 더미데이터 50명 추가 db에 데이터가 없다면 실행
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user", Integer.class);
        if (count != null && count > 0) {
            log.info("이미 user 데이터가 존재합니다 ({}명). 더미 데이터 삽입 건너뜀.", count);
            return;
        }

        int totalUsers = 50; // 삽입할 유저 수
        log.info("더미 데이터 {}건 삽입 시작...", totalUsers);

        String password123 = passwordEncoder.encode("password123");
        List<Object[]> userBatch = new ArrayList<>();
        for (int i = 1; i <= totalUsers; i++) {
            userBatch.add(new Object[]{
                    "user" + i + "@gmail.com", // email
                    "user" + i,                // nickname
                    password123,               // password (BCrypt 인코딩)
                    null,                      // provider
                    "ROLE_MEMBER"              // role
            });
        }

        jdbcTemplate.batchUpdate(
                "INSERT INTO user (email, nickname, password, provider, role) VALUES (?, ?, ?, ?, ?)",
                userBatch
        );

        log.info("user {}명 데이터 삽입 완료!", totalUsers);
    }

}
