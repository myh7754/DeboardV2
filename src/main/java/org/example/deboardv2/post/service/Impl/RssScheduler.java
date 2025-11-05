package org.example.deboardv2.post.service.Impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RssScheduler {
    private final RssService rssService;

    private static final String[] RSS_FEEDS = {
            "https://myh7754.tistory.com/rss"
    };

    @Scheduled(cron = "0 0 * * * *") // 매 정시마다 실행
//    @Scheduled(fixedRate = 10000)
    public void fetchAllRssFeeds() throws Exception {
        for (String feedUrl : RSS_FEEDS) {
            try {
                log.warn("fetch rss from {}", feedUrl);
                rssService.fetchRssFeed(feedUrl);
            } catch (Exception e) {
                log.error("fail to fetch rss");
            }
        }
    }
}
