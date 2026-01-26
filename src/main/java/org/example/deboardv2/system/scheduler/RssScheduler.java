package org.example.deboardv2.system.scheduler;

import com.rometools.rome.feed.synd.SyndEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.service.PostService;
import org.example.deboardv2.redis.service.RedisService;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.domain.RssPost;
import org.example.deboardv2.rss.dto.Candidate;
import org.example.deboardv2.rss.dto.FeedFetchResult;
import org.example.deboardv2.rss.service.AsyncRssService;
import org.example.deboardv2.rss.service.ExternalAuthorService;
import org.example.deboardv2.rss.service.FeedService;
import org.example.deboardv2.rss.service.RssParserService;
import org.example.deboardv2.user.entity.ExternalAuthor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RssScheduler {
    private final FeedService feedService;
    private final AsyncRssService asyncRssService;
    private final RedisService redisService;
    private final RssParserService rssParserService;
    private final PostService postService;
    private final ExternalAuthorService externalAuthorService;

        @Scheduled(cron = "0 0 * * * *") // 매 정시마다 실행
//    @Scheduled(cron = "0 0/5 * * * *") // 5분마다 실행
//    @Scheduled(fixedRate = 60_000) // 1분마다 실행
    public void fetchAllRssFeeds() throws Exception {
        List<Feed> allFeeds = feedService.getAllFeeds();
        List<CompletableFuture<Long>> futures = new ArrayList<>();
        long totalStartTime = System.currentTimeMillis();
        for (Feed feed : allFeeds) {
            // 비동기로 피드별 수집 작업 던지기
            CompletableFuture<Long> future = asyncRssService.collectAndSave(feed);
            futures.add(future);
        }

        try {
            // 모든 작업이 완료될 때까지 대기하는 "통합 관리자" 생성
            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

            // 최대 10분간 모든 작업이 끝나길 기다림
            allOf.get(10, TimeUnit.MINUTES);
            // 3. 404 에러 등으로 실패한 ID 리스트 추출
            List<Long> failedIds = futures.stream()
                    .map(f -> f.join())
                    .filter(Objects::nonNull)
                    .toList();
            long totalDuration = System.currentTimeMillis() - totalStartTime;
            // 4. 실패한 피드들을 한 번에 비활성화 처리
            if (!failedIds.isEmpty()) {
                feedService.disableFeeds(failedIds);
                log.info("{} 개의 피드를 비활성화 처리했습니다.", failedIds.size());
            }
            log.info("전체 소요 시간: {}ms (약 {}초)", totalDuration, totalDuration / 1000.0);
            log.info("모든 RSS 수집 작업이 성공적으로 완료되었습니다.");
        } catch (TimeoutException e) {
            log.warn("일부 RSS 작업이 10분 안에 완료되지 않았습니다 (타임아웃).");
        } catch (Exception e) {
            log.error("RSS 수집 중 오류가 발생했습니다.", e);
        }
    }
}
