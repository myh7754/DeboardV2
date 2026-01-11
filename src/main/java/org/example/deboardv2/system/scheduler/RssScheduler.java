package org.example.deboardv2.system.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.service.AsyncRssService;
import org.example.deboardv2.rss.service.FeedService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RssScheduler {
    private final FeedService feedService;
    private final AsyncRssService asyncRssService;

//    @Scheduled(cron = "0 0 * * * *") // 매 정시마다 실행
    @Scheduled(cron = "0 0/5 * * * *") // 5분마다 실행
//    @Scheduled(fixedRate = 60_000) // 1분마다 실행
    public void fetchAllRssFeeds() throws Exception {
        List<Feed> allFeeds = feedService.getAllFeeds();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Feed feed : allFeeds) {
            // 비동기로 피드별 수집 작업 던지기
            CompletableFuture<Void> future = asyncRssService.collectAndSavePosts(feed);
            futures.add(future);
        }

        try {
            // 모든 작업이 완료될 때까지 대기하는 "통합 관리자" 생성
            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            // 최대 10분간 모든 작업이 끝나길 기다림
            allOf.get(10, TimeUnit.MINUTES);
            log.info("모든 RSS 수집 작업이 성공적으로 완료되었습니다.");
        } catch (TimeoutException e) {
            log.warn("일부 RSS 작업이 10분 안에 완료되지 않았습니다 (타임아웃).");
        } catch (Exception e) {
            log.error("RSS 수집 중 오류가 발생했습니다.", e);
        }
    }


}
