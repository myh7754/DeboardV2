package org.example.deboardv2.rss.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.domain.RssPost;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.rss.domain.UserFeed;
import org.example.deboardv2.rss.repository.FeedRepository;
import org.example.deboardv2.rss.repository.UserFeedRepository;
import org.example.deboardv2.system.exception.CustomException;
import org.example.deboardv2.system.exception.ErrorCode;
import org.example.deboardv2.user.entity.ExternalAuthor;
import org.example.deboardv2.user.entity.User;
import org.example.deboardv2.user.repository.ExternalAuthorRepository;
import org.example.deboardv2.user.service.UserService;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RssService {
    private final PostRepository postRepository;
    private final ExternalAuthorRepository externalAuthorRepository;
    private final List<RssParserStrategy> parserStrategies;
    private final FeedRepository feedRepository;
    private final UserFeedRepository userFeedRepository;
    private final UserService userService;

    // rss url에서 글을 읽어와 post entity로 저장
    public void fetchRssFeed(String feedUrl, Feed rssFeed) throws Exception {
        URL url = new URL(rssFeed.getFeedUrl());
        SAXBuilder saxBuilder = new SAXBuilder();
        Document document = saxBuilder.build(url);
        Element channel = document.getRootElement().getChild("channel");
        List<Element> rawItems = channel.getChildren("item");
        // RSS을 XML로 직접 파싱하여 MAP으로 변환
        Map<String, Element> itemMap = rawItems.stream()
                .collect(Collectors.toMap(
                        item -> item.getChildText("link"),
                        item -> item
                ));

        SyndFeedInput input = new SyndFeedInput();
        input.setPreserveWireFeed(true);
        // 피드 전체
        SyndFeed feed = input.build(new XmlReader(url));
        // 피드의 개별 게시글들
        List<SyndEntry> entries = feed.getEntries();

        // url에 맞는 parser선택
        RssParserStrategy parser = parserStrategies.stream()
                .filter(p -> p.supports(feedUrl))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 블로그입니다"));
        Set<String> entryLinks = entries.stream()
                .map(SyndEntry::getLink)
                .collect(Collectors.toSet());

        Set<String> existingLinks = postRepository.findExistingLinksByFeed(rssFeed, entryLinks);
        
        // 성능 개선: 배치 처리로 변경
        // 1. 새로 저장할 entry만 필터링
        List<SyndEntry> newEntries = entries.stream()
                .filter(entry -> !existingLinks.contains(entry.getLink()))
                .collect(Collectors.toList());
        
        if (newEntries.isEmpty()) {
            return; // 새 항목이 없으면 조기 종료
        }
        
        // 2. 배치로 저장 (트랜잭션 1번, 배치 INSERT)
        saveAllBatch(newEntries, feedUrl, rssFeed, parser, itemMap);
    }

    public void fetchRssFeed(String feedUrl, UserFeed userFeed) throws Exception {
        URL url = new URL(feedUrl);
        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed = input.build(new XmlReader(url));

        List<SyndEntry> entries = feed.getEntries();

        // url에 맞는 parser선택
        RssParserStrategy parser = parserStrategies.stream()
                .filter(p -> p.supports(feedUrl))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 블로그입니다"));

        Set<String> entryLinks = entries.stream()
                .map(SyndEntry::getLink)
                .collect(Collectors.toSet());
        Set<String> existingLinksByUserFeed = postRepository.findExistingLinksByUserFeed(userFeed, entryLinks);

        // 성능 개선: 배치 처리로 변경
        List<SyndEntry> newEntries = entries.stream()
                .filter(entry -> !existingLinksByUserFeed.contains(entry.getLink()))
                .collect(Collectors.toList());
        
        if (newEntries.isEmpty()) {
            return; // 새 항목이 없으면 조기 종료
        }
        
        // 배치로 저장
        saveAllBatchForUserFeed(newEntries, feedUrl, userFeed, parser);
    }

    /**
     * 개별 저장 (레거시 - 호환성 유지용)
     */
    @Transactional
    protected void saveIfNew(RssPost rssPost, String feedUrl) throws Exception {
        ExternalAuthor author = externalAuthorRepository
                .findByNameAndSourceUrl(rssPost.getAuthor(), feedUrl)
                .orElseGet(() -> {
                    ExternalAuthor newAuthor = new ExternalAuthor();
                    newAuthor.update(rssPost.getAuthor(), feedUrl);
                    return externalAuthorRepository.save(newAuthor);
                });

        Post post = Post.fromRss(
                rssPost.getTitle(),
                rssPost.getContent(),
                rssPost.getImage(),
                rssPost.getLink(),
                rssPost.getPublishedAt(),
                author,
                rssPost.getFeed(),
                rssPost.getUserFeed()
        );
        postRepository.save(post);
    }

    /**
     * 성능 개선: 배치 저장 (Feed용)
     * - 트랜잭션 1번
     * - Author 배치 조회
     * - Post 배치 저장
     */
    @Transactional
    protected void saveAllBatch(List<SyndEntry> entries, String feedUrl, Feed rssFeed, 
                                RssParserStrategy parser, Map<String, Element> itemMap) throws Exception {
        // 1. 모든 RssPost 파싱
        List<RssPost> rssPosts = new ArrayList<>();
        for (SyndEntry entry : entries) {
            Element element = itemMap.get(entry.getLink());
            RssPost rssPost = parser.parse(entry, feedUrl, element);
            rssPost.setFeed(rssFeed);
            rssPosts.add(rssPost);
        }
        
        // 2. 필요한 모든 author 이름 수집 (중복 제거) : 중복 제거하는 이유는 작성자는 게시글마다 새로 만들면 안되는 객체이기 때문
        Set<String> authorNames = rssPosts.stream()
                .map(RssPost::getAuthor)
                .collect(Collectors.toSet());
        
        // 3. Author 배치 조회 (N번 쿼리 -> 1번 쿼리) : 한번에 필요한 작성자들이 있다면 전부 조회
        List<ExternalAuthor> existingAuthors = externalAuthorRepository
                .findBySourceUrlAndNamesIn(feedUrl, authorNames);
        Map<String, ExternalAuthor> authorMap = existingAuthors.stream()
                .collect(Collectors.toMap(
                        ExternalAuthor::getName,
                        author -> author,
                        (existing, replacement) -> existing
                ));
        
        // 4. 없는 author는 생성 (메모리에서만) : 만약 조회해온 작성자에 새로운 작성자가 포함이 안되어 있다면 저장
        List<ExternalAuthor> newAuthors = new ArrayList<>();
        for (String authorName : authorNames) {
            if (!authorMap.containsKey(authorName)) {
                ExternalAuthor newAuthor = new ExternalAuthor();
                newAuthor.update(authorName, feedUrl);
                authorMap.put(authorName, newAuthor);
                newAuthors.add(newAuthor);
            }
        }
        
        // 5. 새 author 배치 저장
        if (!newAuthors.isEmpty()) {
            externalAuthorRepository.saveAll(newAuthors);
        }
        
        // 6. Post 엔티티 생성 및 배치 저장
        List<Post> posts = rssPosts.stream()
                .map(rssPost -> Post.fromRss(
                        rssPost.getTitle(),
                        rssPost.getContent(),
                        rssPost.getImage(),
                        rssPost.getLink(),
                        rssPost.getPublishedAt(),
                        authorMap.get(rssPost.getAuthor()),
                        rssPost.getFeed(),
                        rssPost.getUserFeed()
                ))
                .collect(Collectors.toList());
        
        postRepository.saveAll(posts); // 배치 INSERT
    }

    /**
     * 성능 개선: 배치 저장 (UserFeed용)
     */
    @Transactional
    protected void saveAllBatchForUserFeed(List<SyndEntry> entries, String feedUrl, UserFeed userFeed,
                                          RssParserStrategy parser) throws Exception {
        // 1. 모든 RssPost 파싱
        List<RssPost> rssPosts = new ArrayList<>();
        for (SyndEntry entry : entries) {
            RssPost rssPost = parser.parse(entry, feedUrl);
            rssPost.setUserFeed(userFeed);
            rssPosts.add(rssPost);
        }
        
        // 2. 필요한 모든 author 이름 수집
        Set<String> authorNames = rssPosts.stream()
                .map(RssPost::getAuthor)
                .collect(Collectors.toSet());
        
        // 3. Author 배치 조회
        List<ExternalAuthor> existingAuthors = externalAuthorRepository
                .findBySourceUrlAndNamesIn(feedUrl, authorNames);
        Map<String, ExternalAuthor> authorMap = existingAuthors.stream()
                .collect(Collectors.toMap(
                        ExternalAuthor::getName,
                        author -> author,
                        (existing, replacement) -> existing
                ));
        
        // 4. 없는 author는 생성
        List<ExternalAuthor> newAuthors = new ArrayList<>();
        for (String authorName : authorNames) {
            if (!authorMap.containsKey(authorName)) {
                ExternalAuthor newAuthor = new ExternalAuthor();
                newAuthor.update(authorName, feedUrl);
                authorMap.put(authorName, newAuthor);
                newAuthors.add(newAuthor);
            }
        }
        
        // 5. 새 author 배치 저장
        if (!newAuthors.isEmpty()) {
            externalAuthorRepository.saveAll(newAuthors);
        }
        
        // 6. Post 엔티티 생성 및 배치 저장
        List<Post> posts = rssPosts.stream()
                .map(rssPost -> Post.fromRss(
                        rssPost.getTitle(),
                        rssPost.getContent(),
                        rssPost.getImage(),
                        rssPost.getLink(),
                        rssPost.getPublishedAt(),
                        authorMap.get(rssPost.getAuthor()),
                        rssPost.getFeed(),
                        rssPost.getUserFeed()
                ))
                .collect(Collectors.toList());
        
        postRepository.saveAll(posts); // 배치 INSERT
    }

    @Transactional
    public Feed registerFeed(String name, String rssUrl) {
        // 블로그 주소를 우리가 정의한 rss parser를 사용할 수 있는지 판단
        RssParserStrategy parser = parserStrategies.stream()
                .filter(p -> p.supports(rssUrl))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 블로그입니다."));
        // 지원하는 블로그라면 resolve 하여 rss 링크로 변환
        String resolvedUrl = parser.resolve(rssUrl);

        // 만약 등록된 rss 링크라면 넘어가기 아니라면 신규 feed로 등록
        if (feedRepository.existsByFeedUrl(resolvedUrl)) {
            throw new CustomException(ErrorCode.DUPLICATED_FEED);
        } else {
            Feed feed = Feed.builder()
                    .siteName(name)
                    .feedUrl(resolvedUrl)
                    .build();
            return feedRepository.save(feed);
        }
    }

    @Transactional(readOnly = true)
    public List<Feed> getAllFeeds() {
        return feedRepository.findAll();
    }

    @Transactional
    public UserFeed registerUserFeed(String blogName, String rssUrl) {
        User user = userService.getCurrentUser();
        RssParserStrategy parser = parserStrategies.stream()
                .filter(p -> p.supports(rssUrl))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 블로그입니다."));

        String resolvedUrl = parser.resolve(rssUrl);

        if (userFeedRepository.existsByUserAndFeedUrl(user, resolvedUrl)) {
            throw new CustomException(ErrorCode.DUPLICATED_USER_FEED);
        } else {
            UserFeed userFeed = UserFeed.builder()
                    .user(user)
                    .siteName(blogName)
                    .feedUrl(resolvedUrl)
                    .build();
            return userFeedRepository.save(userFeed);
        }
    }

    @Transactional
    public void deleteFeed(Long id) {
        feedRepository.deleteById(id);
    }

    // 사용자 피드 목록
    @Transactional(readOnly = true)
    public List<UserFeed> getUserFeeds(User user) {
        return userFeedRepository.findAllByUser(user);
    }

    @Transactional(readOnly = true)
    public List<UserFeed> getAllUserFeeds() {
        return userFeedRepository.findAll();
    }

    @Transactional
    public void deleteUserFeed(Long id) {
        userFeedRepository.deleteById(id);
    }


}
