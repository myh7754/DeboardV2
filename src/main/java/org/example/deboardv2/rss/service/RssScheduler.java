package org.example.deboardv2.rss.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.domain.UserFeed;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

//private static final String[] RSS_FEEDS = {
//            "https://myh7754.tistory.com/rss", // 내 개인 개발 블로그
//            "https://tech.kakao.com/feed/", // 카카오 개발블로그
//            "https://medium.com/feed/daangn", // 당근 기술 블로그
//            "https://toss.tech/rss.xml", //토스 기술 블로그
//
//    };
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
//            try {
//                rssService.fetchRssFeed(feed.getFeedUrl(), feed);
//            } catch (Exception e) {
//                log.error("fail to fetch rss",e);
//            }
        }

        List<UserFeed> allUserFeeds = rssService.getAllUserFeeds();
        for (UserFeed userFeed : allUserFeeds) {
            futures.add(asyncRssService.processUserFeed(userFeed));
//            try {
//                rssService.fetchRssFeed(userFeed.getFeedUrl(), userFeed);
//            } catch (Exception e) {
//                log.error("fail to fetch userFeed",e);
//            }
        }

        // 대기: 모든 작업 완료 또는 타임아웃 (예: 10분)
        // 만약 작업이 실패했을 경우 해당 작업만 따로 빼서 처리 가능
        try {
            CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            all.get(10, TimeUnit.MINUTES); // 타임아웃은 환경에 맞게 조정
        } catch (Exception e) {
            log.warn("RSS 전체 처리 완료 대기 중 예외 또는 타임아웃", e);
            // 타임아웃 시에는 실패한 feed만 나중에 재시도하도록 별도 로직 권장
        }


    }

    public void fetchAllRssFeedsWithOut() throws Exception {
        List<Feed> feeds = rssService.getAllFeeds();
        for (Feed feed : feeds) {
            try {
                rssService.fetchRssFeedWithOutRefactor(feed.getFeedUrl(), feed);
            } catch (Exception e) {
                log.error("fail to fetch rss",e);
            }
        }

        List<UserFeed> allUserFeeds = rssService.getAllUserFeeds();
        for (UserFeed userFeed : allUserFeeds) {try {
                rssService.fetchRssFeedWithOutRefactor(userFeed.getFeedUrl(), userFeed);
            } catch (Exception e) {
                log.error("fail to fetch userFeed",e);
            }
        }



    }


}
