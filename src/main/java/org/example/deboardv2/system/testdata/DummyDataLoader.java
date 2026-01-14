package org.example.deboardv2.system.testdata;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.comment.repository.CommentsRepository;
import org.example.deboardv2.likes.repository.LikesRepository;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.rss.parser.RssParserStrategy;
import org.example.deboardv2.rss.service.RssParserService;
import org.example.deboardv2.user.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Component
@RequiredArgsConstructor
@Slf4j
public class DummyDataLoader implements CommandLineRunner {

    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final RssParserService rssParserService;
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

        String[][] feedsToInsert = {
                {"영훈블로그", "https://myh7754.tistory.com", "PRIVATE"},
                {"망나니 개발자", "https://mangkyu.tistory.com", "PRIVATE"},
                {"개발 놀이터", "https://coding-review.tistory.com", "PRIVATE"},
                {"딩코딩코", "https://velog.io/@academey/posts", "PRIVATE"},
                {"카카오기술블로그", "https://tech.kakao.com/blog", "PUBLIC"},
                {"우아한형제들 기술블로그", "https://techblog.woowahan.com/", "PUBLIC"},
                {"기억보단 기록을", "https://jojoldu.tistory.com/", "PRIVATE"},
                {"블로그1", "https://lsdiary.tistory.com/", "PRIVATE"},
                {"블로그2", "https://liberal-arts-developer.tistory.com/", "PRIVATE"},
                {"블로그3", "https://velog.io/@heoseungyeon/posts", "PRIVATE"},
                {"블로그4", "https://youwjune.tistory.com/",  "PRIVATE"}


        };
        for (String[] feedInfo : feedsToInsert) {
            String originalUrl = feedInfo[1];
            String resolvedUrl = originalUrl;

            try {
                // 전략 패턴을 사용하여 각 블로그 플랫폼에 맞는 RSS URL로 변환
                RssParserStrategy strategy = rssParserService.selectParser(originalUrl);
                resolvedUrl = strategy.resolve(originalUrl);
            } catch (Exception e) {
                log.warn("URL 처리 중 오류 발생: {}, 원본 URL 사용", originalUrl);
            }
            jdbcTemplate.update("INSERT INTO feed (site_name, feed_url, feed_type) VALUES (?, ?, ?)",
                    feedInfo[0], resolvedUrl, feedInfo[2]);
        }

        String[] targetFeedUrls = {
                "https://myh7754.tistory.com",
                "https://mangkyu.tistory.com",
                "https://coding-review.tistory.com",
                "https://velog.io/@academey/posts",
                "https://jojoldu.tistory.com/",
                "https://lsdiary.tistory.com/",
                "https://liberal-arts-developer.tistory.com/",
                "https://velog.io/@heoseungyeon/posts",
                "https://youwjune.tistory.com/"
        };

        for (String url : targetFeedUrls) {
            RssParserStrategy strategy = rssParserService.selectParser(url);
            String resolveUrl = strategy.resolve(url);

            // 2) 변환된 URL로 feed_id와 site_name을 조회합니다.
            // (DB에는 이미 resolvedUrl로 들어가 있기 때문입니다.)
            Map<String, Object> feedData = jdbcTemplate.queryForMap(
                    "SELECT id, site_name FROM feed WHERE feed_url = ?",
                    resolveUrl
            );

            Long feedId = (Long) feedData.get("id");
            String siteName = (String) feedData.get("site_name");

            // 3) 구독 정보 생성 (1~100번 유저)
            List<Object[]> subBatch = new ArrayList<>();
            for (long userId = 1; userId <= 100; userId++) {
                subBatch.add(new Object[]{
                        siteName, // custom_name
                        feedId,   // feed_id
                        userId    // user_id
                });
            }

            jdbcTemplate.batchUpdate(
                    "INSERT INTO feed_subscription (custom_name, feed_id, user_id) VALUES (?, ?, ?)",
                    subBatch
            );

            log.info("구독 추가 완료: {} (URL: {})", siteName, resolveUrl);

        }


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
