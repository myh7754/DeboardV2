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
                {"블로그4", "https://youwjune.tistory.com/",  "PRIVATE"},

                // 추가 데이터
                {"중년금융생활", "https://middleage-finance-life.tistory.com/", "PRIVATE"},
                {"Labeled", "https://labeled.tistory.com/", "PRIVATE"},
                {"Today Trip", "https://todaytrip.tistory.com/", "PRIVATE"},
                {"Dush12", "https://dush12.tistory.com/", "PRIVATE"},
                {"Mountain59", "https://mountain59.tistory.com/", "PRIVATE"},
                {"Ribi", "https://ribi.tistory.com/", "PRIVATE"},
                {"Choeseunggu", "https://choeseunggu.tistory.com/", "PRIVATE"},
                {"희희네 티타임", "https://heeheene-tea.tistory.com/", "PRIVATE"},
                {"Dreamer418", "https://dreamer418.tistory.com/", "PRIVATE"},
                {"Noneo", "https://noneo.tistory.com/", "PRIVATE"},
                {"크립토스토리", "https://cryptostory1217.tistory.com/", "PRIVATE"},
                {"PatchPink", "https://patchpink0000.tistory.com/", "PRIVATE"},
                {"Riverive", "https://riverive.tistory.com/", "PRIVATE"},
                {"Stedi", "https://stedi.tistory.com/", "PRIVATE"},
                {"Wezard4u", "https://wezard4u.tistory.com/", "PRIVATE"},
                {"원당컴", "https://wondangcom.tistory.com/", "PRIVATE"},
                {"Chounsoon788", "https://chounsoon788.tistory.com/", "PRIVATE"},
                {"맞쟁이", "https://majjaeng.tistory.com/", "PRIVATE"},
                {"Ejssdaddy", "https://ejssdaddy.tistory.com/", "PRIVATE"},
                {"Tutoria", "https://tutoria.tistory.com/", "PRIVATE"},
                {"RT Model", "https://rtmodel.tistory.com/", "PRIVATE"},
                {"Chef Choice", "https://chef-choice.tistory.com/", "PRIVATE"},
                {"Stephinwien", "https://stephinwien.tistory.com/", "PRIVATE"},
                {"Yeongk2813", "https://yeongk2813.tistory.com/", "PRIVATE"},
                {"Lubi Happy", "https://lubi-happy.tistory.com/", "PRIVATE"},
                {"Sweet Basil", "https://asweetbasil.tistory.com/", "PRIVATE"},
                {"Lovely Days", "https://lovely-days.tistory.com/", "PRIVATE"},
                {"Tallpike", "https://tallpike.tistory.com/", "PRIVATE"},
                {"Mingky Hyung-a", "https://mingky-hyung-a.tistory.com/", "PRIVATE"},
                {"Start of Trip", "https://start-of-trip.tistory.com/", "PRIVATE"},
                {"Jyshine24", "https://jyshine24.tistory.com/", "PRIVATE"},
                {"Shy Review", "https://shyreviewdiary.tistory.com/", "PRIVATE"},
                {"Pms46", "https://pms46.tistory.com/", "PRIVATE"},
                {"Ainiesta8", "https://ainiesta8.tistory.com/", "PRIVATE"},
                {"Feeling Look", "https://feelinglook.tistory.com/", "PRIVATE"},
                {"Jongamk", "https://jongamk.tistory.com/", "PRIVATE"},
                {"RGY0409", "https://rgy0409.tistory.com/", "PRIVATE"},
                {"Gooseskin", "https://gooseskin.tistory.com/", "PRIVATE"},
                {"Dhsrkwrjt", "https://dhsrkwrjt.tistory.com/", "PRIVATE"},
                {"Lim826bk", "https://lim826bk.tistory.com/", "PRIVATE"}
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
                "https://youwjune.tistory.com/",

                "https://middleage-finance-life.tistory.com/",
                "https://labeled.tistory.com/",
                "https://todaytrip.tistory.com/",
                "https://dush12.tistory.com/",
                "https://mountain59.tistory.com/",
                "https://ribi.tistory.com/",
                "https://choeseunggu.tistory.com/",
                "https://heeheene-tea.tistory.com/",
                "https://dreamer418.tistory.com/",
                "https://noneo.tistory.com/",
                "https://cryptostory1217.tistory.com/",
                "https://patchpink0000.tistory.com/",
                "https://riverive.tistory.com/",
                "https://stedi.tistory.com/",
                "https://wezard4u.tistory.com/",
                "https://wondangcom.tistory.com/",
                "https://chounsoon788.tistory.com/",
                "https://majjaeng.tistory.com/",
                "https://ejssdaddy.tistory.com/",
                "https://tutoria.tistory.com/",
                "https://rtmodel.tistory.com/",
                "https://chef-choice.tistory.com/",
                "https://stephinwien.tistory.com/",
                "https://yeongk2813.tistory.com/",
                "https://lubi-happy.tistory.com/",
                "https://asweetbasil.tistory.com/",
                "https://lovely-days.tistory.com/",
                "https://tallpike.tistory.com/",
                "https://mingky-hyung-a.tistory.com/",
                "https://start-of-trip.tistory.com/",
                "https://jyshine24.tistory.com/",
                "https://shyreviewdiary.tistory.com/",
                "https://pms46.tistory.com/",
                "https://ainiesta8.tistory.com/",
                "https://feelinglook.tistory.com/",
                "https://jongamk.tistory.com/",
                "https://rgy0409.tistory.com/",
                "https://gooseskin.tistory.com/",
                "https://dhsrkwrjt.tistory.com/",
                "https://lim826bk.tistory.com/"

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
        int total = 100_000;
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
