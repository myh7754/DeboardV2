package org.example.deboardv2.rss.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.service.PostService;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.parser.RssParserStrategy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
@RequiredArgsConstructor
public class AsyncRssService {
    private final RssParserService rssParserService;
    private final RssFetchService rssFetchService;
    private final PostService postService;

    @Async("rssTaskExecutor")
    public CompletableFuture<Void> collectAndSavePosts(Feed feed) throws Exception {
        // 측정 시작
//        long startTime = System.currentTimeMillis();
        RssParserStrategy selectParser = rssParserService.selectParser(feed.getFeedUrl());
        SyndFeed syndFeed = rssFetchService.fetchSyndFeed(feed.getFeedUrl());
        // 측정 종료
//        long endTime = System.currentTimeMillis();
//        log.info("RSS Fetch Time ({}): {}ms", feed.getFeedUrl(), (endTime - startTime));
        List<SyndEntry> newEntries = rssParserService.extractNewEntries(syndFeed, feed);
        List<Post> rssPosts = rssParserService.parseNewEntries(newEntries, selectParser,feed);
        postService.saveBatch(rssPosts);
        return CompletableFuture.completedFuture(null);
    }
}
