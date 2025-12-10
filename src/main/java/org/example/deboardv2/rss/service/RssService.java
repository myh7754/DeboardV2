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

        Set<String> existingLinks = postRepository.findExistingLinksByFeed(rssFeed, entryLinks);
        for (SyndEntry entry : entries) {
            if (existingLinks.contains(entry.getLink())) {
                continue;
            }
//            if (postRepository.existsByLink(entry.getLink())) {
//                continue ;
//            }
            Element element = itemMap.get(entry.getLink());
            RssPost rssPost = parser.parse(entry, feedUrl, element);
            rssPost.setFeed(rssFeed);
            saveIfNew(rssPost, feedUrl);
        }
    }

    public void fetchRssFeedWithOutRefactor(String feedUrl, Feed rssFeed) throws Exception {
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
        SyndFeed feed = input.build(new XmlReader(url));
        List<SyndEntry> entries = feed.getEntries();

        // url에 맞는 parser선택
        RssParserStrategy parser = parserStrategies.stream()
                .filter(p -> p.supports(feedUrl))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 블로그입니다"));

        for (SyndEntry entry : entries) {
            if (postRepository.existsByLink(entry.getLink())) {
                continue ;
            }
            Element element = itemMap.get(entry.getLink());
            RssPost rssPost = parser.parse(entry, feedUrl, element);
            rssPost.setFeed(rssFeed);
            saveIfNew(rssPost, feedUrl);
        }
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

        for (SyndEntry entry : entries) {
            if (existingLinksByUserFeed.contains(entry.getLink())) {
                continue;
            }
//            if (postRepository.existsByLinkAndUserFeed(entry.getLink(), userFeed)) {
//                return ;
//            }
            RssPost rssPost = parser.parse(entry, feedUrl);
            rssPost.setUserFeed(userFeed);
            saveIfNew(rssPost, feedUrl);
        }
    }

    public void fetchRssFeedWithOutRefactor(String feedUrl, UserFeed userFeed) throws Exception {
        URL url = new URL(feedUrl);
        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed = input.build(new XmlReader(url));

        List<SyndEntry> entries = feed.getEntries();

        // url에 맞는 parser선택
        RssParserStrategy parser = parserStrategies.stream()
                .filter(p -> p.supports(feedUrl))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 블로그입니다"));

        for (SyndEntry entry : entries) {
            if (postRepository.existsByLinkAndUserFeed(entry.getLink(), userFeed)) {
                return ;
            }
            RssPost rssPost = parser.parse(entry, feedUrl);
            rssPost.setUserFeed(userFeed);
            saveIfNew(rssPost, feedUrl);
        }
    }

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
                rssPost.getUserFeed() // 여기서 UserFeed가 있다면 getUserFeed를 Feed가 있다면 getFeed를
        );
        postRepository.save(post);
    }

    @Transactional
    public Feed registerFeed(String name, String rssUrl) {
        RssParserStrategy parser = parserStrategies.stream()
                .filter(p -> p.supports(rssUrl))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 블로그입니다."));
        String resolvedUrl = parser.resolve(rssUrl);

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
