package org.example.deboardv2.system.testdata;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.comment.repository.CommentsRepository;
import org.example.deboardv2.likes.repository.LikesRepository;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.service.AsyncRssService;
import org.example.deboardv2.rss.service.RssService;
import org.example.deboardv2.user.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


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
    private final RssService rssService;
    private final AsyncRssService asyncRssService;

    @Override
    public void run(String... args) throws Exception {

        // EC2 배포용 시작 더미데이터 50명 추가 db에 데이터가 없다면 실행
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user", Integer.class);
        if (count != null && count > 0) {
            log.info("이미 user 데이터가 존재합니다 ({}명). 더미 데이터 삽입 건너뜀.", count);
            return;
        }

        Feed feed = rssService.registerFeed("카카오 기술 블로그","tech.kakao.com/blog");
        asyncRssService.processFeed(feed);
        int totalUsers = 3000; // 삽입할 유저 수
        log.info("더미 데이터 유저 {}건 삽입 시작...", totalUsers);

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

        int total = 1_000_000;
        int batchSize = 5000;

        log.info("더미 데이터 post {}건 삽입 시작...", total);

        // Post 데이터도 동일한 방식으로 batch insert
        List<Object[]> postBatch = new ArrayList<>();
        LocalDateTime now = LocalDateTime.of(2010, 1, 1, 0, 0, 0);
        for (int i = 1; i <= total; i++) {
            postBatch.add(new Object[]{
                    0,                                  // like_count (NOT NULL)
                    now,                                // created_at (NOT NULL)
                    now,                               // updated_at (NULL 허용)
                    1L,                                 // user_id
                    "content" + i,                      // content
                    null,                               // image (NULL 허용)
                    "title" + i                         // title
            });

            if (i % batchSize == 0) {
                jdbcTemplate.batchUpdate(
                        "INSERT INTO post (like_count, created_at, updated_at, user_id, content, image, title) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        postBatch
                );
                postBatch.clear();
                log.info("{} posts inserted...", i);
            }
        }

        if (!postBatch.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO post (like_count, created_at, updated_at, user_id, content, image, title) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    postBatch
            );
        }

        log.info("post 데이터 삽입 완료!");

        log.info("user {}명 데이터 삽입 완료!", totalUsers);
    }

}
