package org.example.deboardv2.rss.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.domain.FeedSubscription;
import org.example.deboardv2.rss.domain.FeedType;
import org.example.deboardv2.rss.parser.RssParserStrategy;
import org.example.deboardv2.rss.repository.FeedRepository;
import org.example.deboardv2.rss.repository.FeedSubscriptionRepository;
import org.example.deboardv2.system.exception.CustomException;
import org.example.deboardv2.system.exception.ErrorCode;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedService {
    private final FeedRepository feedRepository;
    private final RssParserService rssParserService;
    private final AsyncRssService asyncRssService;
    private final UserService userService;
    private final FeedSubscriptionRepository feedSubscriptionRepository;

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

    @Transactional(readOnly = true)
    public List<Feed> getAllFeeds() {
        return feedRepository.findAll();
    }

    @Transactional
    public void subscribeUserFeed(String name , String url) throws Exception {
        User referenceUser = userService.getCurrentUserReferenceById();

        Feed feed = createOrGetFeed(name, url);
        if (feedSubscriptionRepository.existsByUserAndFeed(referenceUser,feed)) {
            throw new CustomException(ErrorCode.ALREADY_SUBSCRIBED);
        }

        FeedSubscription feedSubscription = FeedSubscription.builder()
                .customName(name)
                .feed(feed)
                .user(referenceUser)
                .build();
        feedSubscriptionRepository.save(feedSubscription);
        asyncRssService.collectAndSavePosts(feed);
    }

    private Feed createOrGetFeed(String name, String url) {
        RssParserStrategy parser = rssParserService.selectParser(url);
        String resolve = parser.resolve(url);
        return feedRepository.findByFeedUrl(resolve)
                .orElseGet(()-> feedRepository.save(
                        Feed.builder()
                                .siteName(name)
                                .feedUrl(resolve)
                                .feedType(FeedType.PRIVATE)
                                .build()
                ));
    }
}


