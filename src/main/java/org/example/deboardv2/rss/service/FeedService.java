package org.example.deboardv2.rss.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.domain.FeedSubscription;
import org.example.deboardv2.rss.domain.FeedType;
import org.example.deboardv2.rss.dto.UserFeedDto;
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
    private final PostRepository postRepository;

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
        return feedRepository.findAllByIsActiveTrue();
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

    @Transactional(readOnly = true)
    public List<UserFeedDto> getUserSubscriptions() {
        User user = userService.getCurrentUserReferenceById();
        return feedSubscriptionRepository.findAllByUser(user).stream()
                .map(sub -> new UserFeedDto(
                        sub.getId(),            // Subscription의 ID (삭제 시 사용)
                        sub.getCustomName(),    // 유저가 지정한 이름
                        sub.getFeed().getFeedUrl()
                ))
                .toList();
    }

    @Transactional
    public void unsubscribe(Long id) throws Exception {
        User user = userService.getCurrentUserReferenceById();
        FeedSubscription subscription = feedSubscriptionRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.SUBSCRIPTION_NOT_FOUND));

        Feed feed = subscription.getFeed();
        // 본인 확인: 다른 사람이 내 구독 정보를 지우면 안됨
        if (!subscription.getUser().getId().equals(user.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        feedSubscriptionRepository.delete(subscription);

        if (feed.getFeedType().equals(FeedType.PRIVATE) &&
            !feedSubscriptionRepository.existsByFeed(feed)) {
            log.info("피드 확인 {}", feed.getId());
            postRepository.deleteByFeed(feed);
            feedRepository.delete(feed);
        }

    }

    @Transactional
    public void disableFeeds(List<Long> failedIds) {
        if (failedIds != null && !failedIds.isEmpty()) {
            feedRepository.disableFeedsByIds(failedIds);
        }
    }
}


