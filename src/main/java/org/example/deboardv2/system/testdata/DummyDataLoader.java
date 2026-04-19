package org.example.deboardv2.system.testdata;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.comment.repository.CommentsRepository;
import org.example.deboardv2.likes.repository.LikesRepository;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.domain.FeedType;
import org.example.deboardv2.rss.parser.RssParserStrategy;
import org.example.deboardv2.rss.repository.FeedRepository;
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
import java.util.Random;


@Component
@RequiredArgsConstructor
@Slf4j
public class DummyDataLoader implements CommandLineRunner {

    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final RssParserService rssParserService;
    private final FeedRepository feedRepository;
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
                {"블로그4", "https://youwjune.tistory.com/", "PRIVATE"},

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
                {"Lim826bk", "https://lim826bk.tistory.com/", "PRIVATE"},
                {"권한없음 페이지", "https://kk.tistory.com/", "PRIVATE"}
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
                "https://lim826bk.tistory.com/",
                "https://kk.tistory.com/"

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
        // 모킹 피드
//        List<Feed> feeds = new ArrayList<>();
//        for (int i = 1; i <= 1000; i++) {
//            feeds.add(Feed.builder()
//                    .feedUrl("http://localhost:8081/mock-rss/" + i)
//                    .siteName("Test Feed " + i)
//                    .isActive(true)
//                    .feedType(FeedType.PRIVATE)
//                    .build());
//        }
//        feedRepository.saveAll(feeds);


        // Post 데이터도 동일한 방식으로 batch insert
        int total = 10_000_000;
        int batchSize = 50000;

        log.info("더미 데이터 post {}건 삽입 시작...", total);

        String[] titles = {
            "Spring Boot 3.x 마이그레이션 삽질기 - @Configuration 변경사항 정리",
            "Java 21 Virtual Thread 도입 후 HikariCP 커넥션 풀 고갈 이슈",
            "MySQL EXPLAIN 분석으로 쿼리 성능 10배 개선한 방법",
            "AWS EC2 t3.small에서 OOM 발생 원인과 JVM 튜닝 방법",
            "Docker Compose로 로컬 개발 환경 구성하기 - MySQL, Redis, Kafka",
            "Redis 캐시 stampede 문제와 SWR 패턴으로 해결한 경험",
            "JPA N+1 문제 완전 정복 - fetch join vs EntityGraph 비교",
            "Kafka Consumer Group 운영하면서 겪은 장애와 대응 방법",
            "React Query로 서버 상태 관리 리팩토링 후기",
            "TypeScript 제네릭 고급 활용법 - infer, conditional types",
            "쿠버네티스 Pod CrashLoopBackOff 원인 분석 가이드",
            "GitHub Actions로 CI/CD 파이프라인 구축 - self-hosted runner 활용",
            "PostgreSQL vs MySQL 성능 비교 - 1000만 건 데이터 기준",
            "Python asyncio로 크롤러 성능 20배 향상시키기",
            "MSA 전환 6개월 회고 - 잘된 것과 잘못된 것",
            "Nginx 리버스 프록시 설정과 SSL 인증서 자동 갱신",
            "IntelliJ IDEA 생산성 높이는 단축키 50선",
            "클린 아키텍처를 실무에 적용하면서 느낀 점",
            "REST API 설계 원칙과 실무에서 타협하는 부분들",
            "Git Flow vs Trunk Based Development - 팀 규모별 선택 기준",
            "JVM GC 튜닝 - G1GC에서 ZGC로 전환한 이유",
            "Elasticsearch 도입기 - MySQL FULLTEXT 한계를 넘어서",
            "Spring Security JWT 인증 구현 시 주의사항",
            "대용량 파일 업로드 S3 Presigned URL로 구현하기",
            "WebSocket vs SSE - 실시간 알림 기능 선택 기준",
            "테스트 코드 작성 습관 들이기 - JUnit5, Mockito 실전",
            "Lombok 사용 시 주의해야 할 5가지",
            "SOLID 원칙을 코드로 설명하는 예제 모음",
            "Rate Limiting 구현 방법 비교 - Token Bucket vs Sliding Window",
            "모놀리식에서 MSA로 점진적 전환하는 Strangler Fig 패턴",
            "QueryDSL로 동적 쿼리 작성하기 - BooleanExpression 조합",
            "HikariCP 커넥션 풀 사이즈 계산하는 공식",
            "Spring Batch로 대용량 데이터 처리 - Chunk 기반 처리",
            "Redis Sorted Set으로 실시간 랭킹 구현하기",
            "Prometheus + Grafana 모니터링 구축 - Spring Boot Actuator 연동",
            "OAuth2 소셜 로그인 구현 - Google, Kakao, Naver",
            "비동기 처리 ThreadPoolTaskExecutor 설정 가이드",
            "MySQL 인덱스 전략 - 복합 인덱스 컬럼 순서의 중요성",
            "이벤트 드리븐 아키텍처 도입 후 달라진 것들",
            "로컬에서 잘 되는데 운영에서만 발생하는 이슈 디버깅 방법",
            "객체지향 설계 - 상속보다 조합을 써야 하는 이유",
            "AWS Lambda 콜드 스타트 줄이는 방법 - Provisioned Concurrency",
            "Flyway로 DB 마이그레이션 관리하기",
            "Spring WebFlux 도입 전에 고려해야 할 것들",
            "코드 리뷰 문화 정착시키기 - 팀에서 겪은 시행착오",
            "HTTP/2, HTTP/3 차이와 실무 적용 시 고려사항",
            "분산 트랜잭션 SAGA 패턴 구현 경험담",
            "Java Stream API 성능 주의사항 - parallel stream 함정",
            "SQS + Lambda로 이벤트 기반 알림 시스템 구축",
            "인덱스를 타는데도 쿼리가 느린 이유 - OFFSET의 함정"
        };
        String[] contentTemplates = {
            "이번 포스팅에서는 %s에 대해 자세히 다뤄보겠습니다. 실무에서 직접 겪은 사례를 바탕으로 작성했으며, 코드 예제와 함께 단계별로 설명합니다. 같은 문제로 고민하시는 분들께 도움이 되길 바랍니다.",
            "최근 프로젝트에서 %s 관련 이슈를 해결하면서 얻은 인사이트를 공유합니다. 처음에는 간단해 보였지만 생각보다 깊은 내용이 있었습니다. 삽질한 시간을 줄여드리고 싶어 정리했습니다.",
            "%s를 도입하면서 팀에서 겪은 시행착오와 최종 결론을 정리했습니다. 공식 문서만으로는 부족했던 부분, 실제 운영 환경에서 다른 부분들을 중점적으로 다룹니다.",
            "오늘은 %s의 내부 동작 원리를 파헤쳐 보겠습니다. 원리를 이해하면 문제가 생겼을 때 훨씬 빠르게 대응할 수 있습니다. 소스 코드 레벨까지 분석한 내용도 포함합니다.",
            "%s에 대해 많은 오해가 있어서 이번 기회에 제대로 정리해보려 합니다. 인터넷에 잘못된 정보도 많고, 공식 문서와 다르게 동작하는 케이스도 있어서 직접 테스트해봤습니다.",
            "실무에서 %s를 적용할 때 반드시 알아야 할 포인트들을 정리했습니다. 이론과 실제 차이, 성능 측정 결과, 운영 노하우까지 담았습니다.",
            "%s 관련해서 팀원들과 자주 나누는 질문들을 모아서 정리했습니다. 신입 개발자분들이 특히 자주 막히는 부분들 위주로 설명합니다."
        };

        Random random = new Random();
        List<Object[]> postBatch = new ArrayList<>();
        LocalDateTime now = LocalDateTime.of(2010, 1, 1, 0, 0, 0);
        for (int i = 1; i <= total; i++) {
            String title = titles[random.nextInt(titles.length)];
            String content = String.format(contentTemplates[random.nextInt(contentTemplates.length)], title);

            postBatch.add(new Object[]{
                    0,                                  // like_count (NOT NULL)
                    now,                                // created_at (NOT NULL)
                    now,                               // updated_at (NULL 허용)
                    1L,                                 // user_id
                    content,                            // content
                    null,                               // image (NULL 허용)
                    title,                              // title
                    true                                // is_public (사용자 작성 글은 항상 공개)
            });

            if (i % batchSize == 0) {
                jdbcTemplate.batchUpdate(
                        "INSERT INTO post (like_count, created_at, updated_at, user_id, content, image, title, is_public) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        postBatch
                );
                postBatch.clear();
                log.info("{} posts inserted...", i);
            }
        }

        if (!postBatch.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO post (like_count, created_at, updated_at, user_id, content, image, title, is_public) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    postBatch
            );
        }

        log.info("post 데이터 삽입 완료!");
    }

}
