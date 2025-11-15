package org.example.deboardv2.rss.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.domain.UserFeed;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RssScheduler {
    private final RssService rssService;

    private static final String[] RSS_FEEDS = {
            "https://myh7754.tistory.com/rss", // 내 개인 개발 블로그
            "https://tech.kakao.com/feed/", // 카카오 개발블로그
            "https://medium.com/feed/daangn", // 당근 기술 블로그
            "https://toss.tech/rss.xml", //토스 기술 블로그

    };

    @Scheduled(cron = "0 0 * * * *") // 매 정시마다 실행
//    @Scheduled(fixedRate = 10000)
    public void fetchAllRssFeeds() throws Exception {
        List<Feed> feeds = rssService.getAllFeeds();
        for (Feed feed : feeds) {
            try {
                rssService.fetchRssFeed(feed.getFeedUrl(), feed);
            } catch (Exception e) {
                log.error("fail to fetch rss");
            }
        }

        List<UserFeed> allUserFeeds = rssService.getAllUserFeeds();
        for (UserFeed userFeed : allUserFeeds) {
            try {
                rssService.fetchRssFeed(userFeed.getFeedUrl(), userFeed);
            } catch (Exception e) {
                log.error("fail to fetch userFeed");
            }
        }
    }


}
