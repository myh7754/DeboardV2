package org.example.deboardv2.rss.service;

import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.domain.FeedSubscription;
import org.example.deboardv2.rss.domain.FeedType;
import org.example.deboardv2.rss.parser.RssParserStrategy;
import org.example.deboardv2.rss.repository.FeedRepository;
import org.example.deboardv2.rss.repository.FeedSubscriptionRepository;
import org.example.deboardv2.system.exception.CustomException;
import org.example.deboardv2.system.exception.ErrorCode;
import org.example.deboardv2.user.dto.SignupRequest;
import org.example.deboardv2.user.dto.TokenBody;
import org.example.deboardv2.user.entity.Role;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class FeedServiceIntegrationTest {

    @Autowired
    FeedService feedService;

    @Autowired
    FeedRepository feedRepository;

    @Autowired
    FeedSubscriptionRepository feedSubscriptionRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    UserRepository userRepository;

    @MockitoBean
    AsyncRssService asyncRssService;

    @MockitoSpyBean
    RssParserService rssParserService;

    private User user;
    private User otherUser;
    private RssParserStrategy mockParser;

    @BeforeEach
    void setUp() throws Exception {
        // mock parser: resolve() returns the url as-is
        mockParser = mock(RssParserStrategy.class);
        doReturn(mockParser).when(rssParserService).selectParser(any());
        when(mockParser.resolve(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // stub asyncRssService to do nothing
        when(asyncRssService.collectAndSavePosts(any())).thenReturn(null);

        // create users
        SignupRequest userRequest = new SignupRequest();
        userRequest.setNickname("feed_test_user");
        userRequest.setEmail("feeduser@test.com");
        userRequest.setPassword("password123!");
        user = userRepository.save(User.toEntity(userRequest));

        SignupRequest otherRequest = new SignupRequest();
        otherRequest.setNickname("feed_other_user");
        otherRequest.setEmail("feedother@test.com");
        otherRequest.setPassword("password123!");
        otherUser = userRepository.save(User.toEntity(otherRequest));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        feedSubscriptionRepository.deleteAll();
        postRepository.deleteAll();
        feedRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void setSecurityContext(User u) {
        TokenBody tokenBody = new TokenBody(u.getId(), u.getNickname(), Role.ROLE_MEMBER);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(tokenBody, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ----------------------------------------------------------------
    // registerFeed()
    // ----------------------------------------------------------------

    @Test
    @DisplayName("registerFeed() - 중복 URL이면 DUPLICATED_FEED 예외 발생")
    void registerFeed_duplicateUrl_throwsDuplicatedFeed() throws Exception {
        // given - pre-save a feed with the same URL
        String url = "https://example.com/rss";
        feedRepository.save(Feed.builder()
                .siteName("existing")
                .feedUrl(url)
                .feedType(FeedType.PUBLIC)
                .build());

        // when & then
        CustomException ex = assertThrows(CustomException.class,
                () -> feedService.registerFeed("new name", url));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DUPLICATED_FEED);
    }

    @Test
    @DisplayName("registerFeed() - 신규 URL이면 Feed 저장, PUBLIC 타입으로 생성")
    void registerFeed_newUrl_savedAsPublic() throws Exception {
        // given
        String url = "https://newblog.com/rss";

        // when
        Feed result = feedService.registerFeed("New Blog", url);

        // then
        assertThat(result.getId()).isNotNull();
        assertThat(result.getFeedUrl()).isEqualTo(url);
        assertThat(result.getFeedType()).isEqualTo(FeedType.PUBLIC);
        assertThat(result.getSiteName()).isEqualTo("New Blog");

        Feed persisted = feedRepository.findById(result.getId()).orElseThrow();
        assertThat(persisted.getFeedType()).isEqualTo(FeedType.PUBLIC);
    }

    // ----------------------------------------------------------------
    // subscribeUserFeed()
    // ----------------------------------------------------------------

    @Test
    @DisplayName("subscribeUserFeed() - 이미 구독한 피드이면 ALREADY_SUBSCRIBED 예외 발생")
    void subscribeUserFeed_alreadySubscribed_throwsAlreadySubscribed() throws Exception {
        // given
        setSecurityContext(user);
        String url = "https://alreadysub.com/rss";

        // first subscription via repository directly (feed already exists)
        Feed feed = feedRepository.save(Feed.builder()
                .siteName("Already Subbed")
                .feedUrl(url)
                .feedType(FeedType.PRIVATE)
                .build());
        feedSubscriptionRepository.save(FeedSubscription.builder()
                .customName("Already Subbed")
                .feed(feed)
                .user(user)
                .build());

        // when & then
        CustomException ex = assertThrows(CustomException.class,
                () -> feedService.subscribeUserFeed("Already Subbed", url));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ALREADY_SUBSCRIBED);
    }

    @Test
    @DisplayName("subscribeUserFeed() - 신규 구독이면 FeedSubscription 저장")
    void subscribeUserFeed_newSubscription_savedSuccessfully() throws Exception {
        // given
        setSecurityContext(user);
        String url = "https://newsub.com/rss";

        // when
        feedService.subscribeUserFeed("New Sub", url);

        // then - FeedSubscription was saved
        List<FeedSubscription> subs = feedSubscriptionRepository.findAllByUser(user);
        assertThat(subs).hasSize(1);

        // verify the feed was created with the correct URL
        assertThat(feedRepository.findByFeedUrl(url)).isPresent();

        // verify subscription exists for user and the newly created feed
        Feed savedFeed = feedRepository.findByFeedUrl(url).orElseThrow();
        assertThat(feedSubscriptionRepository.existsByUserAndFeed(user, savedFeed)).isTrue();
    }

    // ----------------------------------------------------------------
    // unsubscribe()
    // ----------------------------------------------------------------

    @Test
    @DisplayName("unsubscribe() - 타인 구독 삭제 시도 시 FORBIDDEN 예외 발생")
    void unsubscribe_otherUserSubscription_throwsForbidden() throws Exception {
        // given - otherUser owns the subscription
        Feed feed = feedRepository.save(Feed.builder()
                .siteName("Some Feed")
                .feedUrl("https://somefeed.com/rss")
                .feedType(FeedType.PRIVATE)
                .build());
        FeedSubscription subscription = feedSubscriptionRepository.save(FeedSubscription.builder()
                .customName("Some Feed")
                .feed(feed)
                .user(otherUser)
                .build());

        // current user is 'user', not 'otherUser'
        setSecurityContext(user);

        // when & then
        CustomException ex = assertThrows(CustomException.class,
                () -> feedService.unsubscribe(subscription.getId()));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("unsubscribe() - PRIVATE 피드의 마지막 구독자 탈퇴 시 Feed + 게시글 전부 삭제")
    void unsubscribe_lastSubscriberOfPrivateFeed_deletesFeedAndPosts() throws Exception {
        // given - save PRIVATE feed, subscription, and posts directly via repositories
        Feed feed = feedRepository.save(Feed.builder()
                .siteName("Private Blog")
                .feedUrl("https://privateblog.com/rss")
                .feedType(FeedType.PRIVATE)
                .build());

        FeedSubscription subscription = feedSubscriptionRepository.save(FeedSubscription.builder()
                .customName("Private Blog")
                .feed(feed)
                .user(user)
                .build());

        // create posts linked to this feed using Post.fromRss
        Post post1 = Post.fromRss("Title 1", "Content 1", null,
                "https://privateblog.com/post/1", LocalDateTime.now(), null, feed);
        Post post2 = Post.fromRss("Title 2", "Content 2", null,
                "https://privateblog.com/post/2", LocalDateTime.now(), null, feed);
        postRepository.save(post1);
        postRepository.save(post2);

        Long feedId = feed.getId();

        // set security context as 'user' (the subscriber)
        setSecurityContext(user);

        // when
        feedService.unsubscribe(subscription.getId());

        // then - feed must be deleted
        assertThat(feedRepository.findById(feedId)).isEmpty();

        // then - posts linked to that feed must be deleted
        List<Post> remainingPosts = postRepository.findAll();
        long postsForFeed = remainingPosts.stream()
                .filter(p -> p.getFeed() != null && p.getFeed().getId().equals(feedId))
                .count();
        assertThat(postsForFeed).isEqualTo(0);
    }

    // ----------------------------------------------------------------
    // disableFeeds()
    // ----------------------------------------------------------------

    @Test
    @DisplayName("disableFeeds() - 실패 피드 ID 목록에 대해 isActive=false 처리")
    void disableFeeds_givenFailedIds_setsIsActiveFalse() {
        // given
        Feed feed1 = feedRepository.save(Feed.builder()
                .siteName("Feed 1")
                .feedUrl("https://feed1.com/rss")
                .feedType(FeedType.PUBLIC)
                .build());
        Feed feed2 = feedRepository.save(Feed.builder()
                .siteName("Feed 2")
                .feedUrl("https://feed2.com/rss")
                .feedType(FeedType.PUBLIC)
                .build());
        Feed feed3 = feedRepository.save(Feed.builder()
                .siteName("Feed 3")
                .feedUrl("https://feed3.com/rss")
                .feedType(FeedType.PUBLIC)
                .build());

        // feed1 and feed2 are "failed"
        List<Long> failedIds = List.of(feed1.getId(), feed2.getId());

        // when
        feedService.disableFeeds(failedIds);

        // then
        Feed disabled1 = feedRepository.findById(feed1.getId()).orElseThrow();
        Feed disabled2 = feedRepository.findById(feed2.getId()).orElseThrow();
        Feed active3 = feedRepository.findById(feed3.getId()).orElseThrow();

        assertThat(disabled1.isActive()).isFalse();
        assertThat(disabled2.isActive()).isFalse();
        assertThat(active3.isActive()).isTrue();
    }
}
