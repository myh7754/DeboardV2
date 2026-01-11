package org.example.deboardv2.system.testdata;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.comment.repository.CommentsRepository;
import org.example.deboardv2.likes.repository.LikesRepository;
import org.example.deboardv2.post.repository.PostRepository;
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
//    private final RssService rssService;
//    private final AsyncRssService asyncRssService;

    @Override
    public void run(String... args) throws Exception {

        // EC2 배포용 시작 더미데이터 50명 추가 db에 데이터가 없다면 실행
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        if (count != null && count > 0) {
            log.info("이미 user 데이터가 존재합니다 ({}명). 더미 데이터 삽입 건너뜀.", count);
            return;
        }
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
                "INSERT INTO users (email, nickname, password, provider, role) VALUES (?, ?, ?, ?, ?)",
                userBatch
        );

        log.info("users {}명 데이터 삽입 완료!", totalUsers);

        log.info("더미 데이터 userFeed 100건 삽입 시작...");
        // 1. 테스트용 공통 Feed 생성 (영훈 블로그)
        String feedUrl = "https://myh7754.tistory.com/rss";
        String siteName = "영훈블로그";
        jdbcTemplate.update(
                "INSERT INTO feed (site_name, feed_url, feed_type) VALUES (?, ?, ?)",
                siteName, feedUrl, "PRIVATE" // Enum이 String으로 저장되는 경우
        );
        Long feedId = jdbcTemplate.queryForObject(
                "SELECT id FROM feed WHERE feed_url = ?", Long.class, feedUrl);
        List<Object[]> subscriptionBatch = new ArrayList<>();
        for (long userId = 1; userId <= 100; userId++) {
            subscriptionBatch.add(new Object[]{
                    siteName, // customName
                    feedId,   // feed_id
                    userId    // user_id
            });
        }

        // batchUpdate를 사용하여 100건을 한 번에 삽입
        jdbcTemplate.batchUpdate(
                "INSERT INTO feed_subscription (custom_name, feed_id, user_id) VALUES (?, ?, ?)",
                subscriptionBatch
        );

        log.info("유저 100명의 구독 데이터(FeedSubscription) 삽입 완료! (대상 Feed ID: {})", feedId);

        // Post 데이터도 동일한 방식으로 batch insert
        int total = 1_000_000;
        int batchSize = 5000;

        log.info("더미 데이터 post {}건 삽입 시작...", total);

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
    }

}
