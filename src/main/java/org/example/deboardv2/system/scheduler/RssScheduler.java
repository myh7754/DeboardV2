package org.example.deboardv2.system.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.domain.UserFeed;
import org.example.deboardv2.rss.service.AsyncRssService;
import org.example.deboardv2.rss.service.RssService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RssScheduler {
    private final RssService rssService;
    private final AsyncRssService asyncRssService;

    @Scheduled(cron = "0 0 * * * *") // 매 정시마다 실행
//    @Scheduled(cron = "0 0/10 * * * *") // 10분마다 실행
//    @Scheduled(fixedRate = 10000)
    public void fetchAllRssFeeds() throws Exception {
        List<Feed> feeds = rssService.getAllFeeds();
        List<CompletableFuture> futures = new ArrayList<>();
        for (Feed feed : feeds) {
            futures.add(asyncRssService.processFeed(feed));
        }

        List<UserFeed> allUserFeeds = rssService.getAllUserFeeds();
        for (UserFeed userFeed : allUserFeeds) {
            futures.add(asyncRssService.processUserFeed(userFeed));
        }

        // 대기: 모든 작업 완료 또는 타임아웃 (예: 10분)
        // 만약 작업이 실패했을 경우 해당 작업만 따로 빼서 처리 가능
        try {
            CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            all.get(10, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("RSS 전체 처리 완료 대기 중 예외 또는 타임아웃", e);
            // 타임아웃 시에는 실패한 feed만 나중에 재시도하도록
        }
    }


}
