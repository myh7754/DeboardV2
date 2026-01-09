package org.example.deboardv2.rss.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import lombok.RequiredArgsConstructor;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.service.PostService;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.parser.RssParserStrategy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class AsyncRssService {
    private final RssParserService rssParserService;
    private final RssFetchService rssFetchService;
    private final PostService postService;

    @Async("rssTaskExecutor")
    public CompletableFuture<Void> collectAndSavePosts(Feed feed) throws Exception {
        RssParserStrategy selectParser = rssParserService.selectParser(feed.getFeedUrl());
        SyndFeed syndFeed = rssFetchService.fetchSyndFeed(feed.getFeedUrl());
        List<SyndEntry> newEntries = rssParserService.extractNewEntries(syndFeed, feed);
        List<Post> rssPosts = rssParserService.parseNewEntries(newEntries, selectParser,feed);
        postService.saveBatch(rssPosts);
        return null;
    }
}
