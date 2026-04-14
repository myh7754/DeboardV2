package org.example.deboardv2.rss.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.service.PostService;
import org.example.deboardv2.redis.RedisKeyConstants;
import org.example.deboardv2.redis.service.RedisService;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.dto.FeedFetchResult;
import org.example.deboardv2.rss.dto.RssFeedData;
import org.example.deboardv2.rss.parser.RssParserStrategy;
import org.jdom2.Element;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

@Component
@Slf4j
@RequiredArgsConstructor
public class AsyncRssService {
    private final RssParserService rssParserService;
    private final RssFetchService rssFetchService;
    private final PostService postService;
    private final RedisService redisService;
    private final Executor rssTaskExecutor;
    private final Semaphore dbLimitSemaphore;

    @Async("rssTaskExecutor")
    public CompletableFuture<Long> collectAndSavePosts(Feed feed) throws Exception {
        try {
            RssParserStrategy selectParser = rssParserService.selectParser(feed.getFeedUrl());
            RssFeedData rssData = rssFetchService.fetchRssData(feed.getFeedUrl());
            SyndFeed syndFeed = rssData.getSyndFeed();
            Map<String, Element> rawMap = rssData.getRawElementMap();

            List<SyndEntry> newEntries = rssParserService.extractNewEntries(syndFeed, feed);
            if (!newEntries.isEmpty()) {
                List<Post> rssPosts = rssParserService.parseNewEntries(newEntries, selectParser, feed, rawMap);
                postService.saveBatch(rssPosts);
                String key = RedisKeyConstants.RSS_FEED + feed.getId();
                List<String> linksToCache = rssPosts.stream()
                        .map(Post::getLink)
                        .toList();
                if (!linksToCache.isEmpty()) {
                    redisService.addAllToZSet(key, linksToCache, 50);
                }
            }

//            log.info("피드 저장 및 캐시 갱신 완료: {}, 저장된 개수: {}", feed.getFeedUrl(), rssPosts.size());

        } catch (java.io.FileNotFoundException e) {
            log.error("rss.feed.not_found feedUrl={}", feed.getFeedUrl());
            return CompletableFuture.completedFuture(feed.getId());
        } catch (Exception e) {
            log.error("rss.collect.failed feedUrl={}", feed.getFeedUrl(), e);
            return CompletableFuture.completedFuture(feed.getId());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Async("fetchRssExecutor")
    public CompletableFuture<Long> collectAndSave(Feed feed) {
        // 1. 네트워크 호출 (비동기)
        return rssFetchService.fetchRssDataAsync(feed.getFeedUrl())
                .thenComposeAsync(rssData -> {
                    RssParserStrategy selectParser = rssParserService.selectParser(feed.getFeedUrl());
                    // 2. 파싱 및 저장 단계로 이동
                    return processParsingAndStorage(feed, rssData, selectParser);
                })
                .exceptionally(e -> {
                    // 네트워크 단계 또는 파싱/저장 단계 중 어디서든 터지면 호출됨
                    log.error("rss.collect.final_failed feedUrl={}", feed.getFeedUrl(), e);
                    return feed.getId(); // 실패 시 피드 ID 반환 (비활성화 대상)
                });
    }

    private CompletableFuture<Long> processParsingAndStorage(Feed feed, RssFeedData rssData, RssParserStrategy parser) {
        return CompletableFuture.supplyAsync(() -> {
            try {

                SyndFeed syndFeed = rssData.getSyndFeed();
                Map<String, Element> rawMap = rssData.getRawElementMap();

                // 중복 체크 및 저장
                List<SyndEntry> newEntries = rssParserService.extractPostListImprove(feed, syndFeed.getEntries());

                if (newEntries.isEmpty()) {
                    log.debug("새로운 게시글 없음: {}", feed.getFeedUrl());
                    return null; // 성공했지만 저장할 게 없는 경우 null (정상)
                }

                List<Post> rssPosts = rssParserService.parseNewEntries(newEntries, parser, feed, rawMap);
                postService.saveBatch(rssPosts);

                // Redis 캐시 갱신
                String key = RedisKeyConstants.RSS_FEED + feed.getId();
                List<String> linksToCache = rssPosts.stream().map(Post::getLink).toList();
                redisService.addAllToZSet(key, linksToCache, 50);

                log.info("수집 완료: {} ({}개 저장)", feed.getFeedUrl(), rssPosts.size());
                return null; // 모든 작업 성공 시 null 반환

            } catch (Exception e) {
                log.error("rss.save.failed feedUrl={}", feed.getFeedUrl(), e);
                // 여기서 ID를 반환해야 exceptionally 혹은 join()에서 에러 피드로 인식함
                return feed.getId();
            } finally {
            }
        }, rssTaskExecutor);
    }
}





