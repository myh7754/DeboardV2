package org.example.deboardv2.rss.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.service.PostService;
import org.example.deboardv2.redis.service.RedisService;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.parser.RssParserStrategy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class AsyncRssService {
    private final RssParserService rssParserService;
    private final RssFetchService rssFetchService;
    private final PostService postService;
    private final RedisService redisService;

    @Async("rssTaskExecutor")
    public CompletableFuture<Long> collectAndSavePosts(Feed feed) throws Exception {
        try {
            // 측정 시작
            long startTime = System.currentTimeMillis();
            RssParserStrategy selectParser = rssParserService.selectParser(feed.getFeedUrl());
            SyndFeed syndFeed = rssFetchService.fetchSyndFeed(feed.getFeedUrl());
            // 측정 종료
            long endTime = System.currentTimeMillis();
            log.info("RSS Fetch Time ({}): {}ms", feed.getFeedUrl(), (endTime - startTime));

            List<SyndEntry> newEntries = rssParserService.extractNewEntries(syndFeed, feed);
            List<Post> rssPosts = rssParserService.parseNewEntries(newEntries, selectParser, feed);
            postService.saveBatch(rssPosts);

            long startTime2 = System.currentTimeMillis();
            String key = "rss:feed:" + feed.getId();
            List<String> linksToCache = rssPosts.stream()
                    .map(Post::getLink)
                    .toList();
            if (!linksToCache.isEmpty()) {
                redisService.addAllToZSet(key, linksToCache, 50);
            }
            log.info("피드 저장 및 캐시 갱신 완료: {}, 저장된 개수: {}", feed.getFeedUrl(), rssPosts.size());

            long endTime2 = System.currentTimeMillis();
            log.info("redis 저장 속도결과 : {}ms", (endTime2 - startTime2));
        } catch (java.io.FileNotFoundException e) {
            log.error("피드 주소를 찾을 수 없습니다 (404): {}", feed.getFeedUrl());
            return CompletableFuture.completedFuture(feed.getId());
        } catch (Exception e) {
            log.error("RSS 수집 중 예외 발생 [{}]: {}", feed.getFeedUrl(), e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }
}
