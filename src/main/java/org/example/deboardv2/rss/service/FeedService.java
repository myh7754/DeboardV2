package org.example.deboardv2.rss.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.domain.FeedType;
import org.example.deboardv2.rss.parser.RssParserStrategy;
import org.example.deboardv2.rss.repository.FeedRepository;
import org.example.deboardv2.system.exception.CustomException;
import org.example.deboardv2.system.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedService {
    private final FeedRepository feedRepository;
    private final RssParserService rssParserService;
    private final AsyncRssService asyncRssService;

    @Transactional
    public Feed registerFeed(String name, String url) throws Exception {
        RssParserStrategy selectParser = rssParserService.selectParser(url);
        String resolve = selectParser.resolve(url);
        if (feedRepository.existsByFeedUrl(resolve)) {
            throw new CustomException(ErrorCode.DUPLICATED_FEED);
        }
        log.info("resolve: {}", resolve);
        Feed build = Feed.builder()
                .feedType(FeedType.PUBLIC)
                .siteName(name)
                .feedUrl(resolve)
                .build();
        Feed feed = feedRepository.save(build);
        asyncRssService.collectAndSavePosts(feed);
        return feedRepository.save(feed);
    }
}


