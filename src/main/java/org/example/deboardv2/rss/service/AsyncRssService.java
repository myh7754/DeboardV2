package org.example.deboardv2.rss.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.domain.UserFeed;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
@RequiredArgsConstructor
public class AsyncRssService {
    private final RssService rssService;

    @Async("rssTaskExecutor")
    public CompletableFuture<Void> processFeed(Feed feed) {
        try {
            rssService.fetchRssFeed(feed.getFeedUrl(),feed);
        } catch (Exception e) {
            log.error("Rss처리 실패 feedUrl = {}", feed.getFeedUrl(), e);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Async("rssTaskExecutor")
    public CompletableFuture<Void> processUserFeed(UserFeed userFeed) {
        try {
            rssService.fetchRssFeed(userFeed.getFeedUrl(), userFeed);
        } catch (Exception e) {
            log.error("UserFeed 처리 실패 feedUrl={}", userFeed.getFeedUrl(), e);
        }
        return CompletableFuture.completedFuture(null);
    }
}