package org.example.deboardv2.rss.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.redis.service.RedisService;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.domain.RssPost;
import org.example.deboardv2.rss.dto.Candidate;
import org.example.deboardv2.rss.dto.FeedFetchResult;
import org.example.deboardv2.rss.parser.RssParserStrategy;
import org.example.deboardv2.user.entity.ExternalAuthor;
import org.example.deboardv2.user.repository.ExternalAuthorRepository;
import org.jdom2.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RssParserService {
    private final List<RssParserStrategy> parserList;
    private final PostRepository postRepository;
    private final ExternalAuthorService externalAuthorService;
    private final ExternalAuthorRepository externalAuthorRepository;
    private final RedisService redisService;

    public RssParserStrategy selectParser(String feedUrl) {
        return parserList.stream()
                .filter(p -> p.supports(feedUrl))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 블로그입니다"));
    }

    // SyndEntry -> RssPost로 변환
    public RssPost parseEntry(SyndEntry entry, String feedUrl) {
        RssParserStrategy parser = selectParser(feedUrl);
        return parser.parse(entry);
    }

    // 새로 갱신된 게시글들만 추출
    public List<SyndEntry> extractNewEntries(SyndFeed feed, Feed dtoFeed) {
        List<SyndEntry> entries = feed.getEntries();
        long startTime = System.currentTimeMillis();
        List<SyndEntry> newEntries = extractPostListImprove(dtoFeed, entries);
        long endTime = System.currentTimeMillis();
        log.info("조회처리 시간 {} : {}ms", dtoFeed.getFeedUrl(), (endTime - startTime));
        return newEntries;
    }

    public List<Post> parseNewEntries(List<SyndEntry> entries, RssParserStrategy parser, Feed feed, Map<String, Element> rawMap) throws Exception {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<RssPost> rssPosts = convertToRssPosts(entries, parser,rawMap);

        // 해당 feed에서 발행된 글의 글쓴이 만들어서 가져오기
        Map<String, ExternalAuthor> authorMap = externalAuthorService.prepareAuthors(rssPosts, feed);

        return rssPosts.stream()
                .map(rssPost -> {
                    ExternalAuthor author = authorMap.get(rssPost.getAuthor());
                    try {
                        return Post.fromRss(
                                rssPost.getTitle(),
                                rssPost.getContent(),
                                rssPost.getImage(),
                                rssPost.getLink(),
                                rssPost.getPublishedAt(),
                                author,
                                feed
                        );
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static List<RssPost> convertToRssPosts(List<SyndEntry> entries, RssParserStrategy parser, Map<String, Element> rawMap) {
        List<RssPost> rssPosts = entries.stream()
                .map(entry -> {
                    try {
                        Element element = rawMap.get(entry.getLink());
                        return parser.parse(entry, element);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        return rssPosts;
    }

    // 중복된 게시글을 필터링
    @Transactional(readOnly = true)
    public List<SyndEntry> extractPostList(Feed dtoFeed, List<SyndEntry> entries) {
        List<String> entriesLinks = entries.stream()
                        .map(SyndEntry::getLink)
                        .collect(Collectors.toList());

        Set<String> alreadyInDb = postRepository.findExistingLinksByFeed(dtoFeed, entriesLinks);

        // 이거 나중에 캐시로 변경
        // 중복 필터링
        log.info("게시글 중복 필터링하기위해 feed로 전부 조회 {}", dtoFeed.getFeedUrl());
        return entries.stream()
                .filter(entry -> !alreadyInDb.contains(entry.getLink()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SyndEntry> extractPostListImprove(Feed dtoFeed, List<SyndEntry> entries) {
        String key = "rss:feed:" + dtoFeed.getId();
        List<String> links = entries.stream()
                        .map(SyndEntry::getLink)
                        .toList();
        if (!redisService.hasKey(key)) {
            Set<String> alreadyInDb = postRepository.findExistingLinksByFeed(dtoFeed, links);
            return entries.stream()
                    .filter(e -> !alreadyInDb.contains(e.getLink()))
                    .collect(Collectors.toList());
        }

        List<Boolean> existenceList = redisService.checkLinksExistence(key, links);
        List<SyndEntry> newEntries = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (!existenceList.get(i)) {
                newEntries.add(entries.get(i));
            }
        }
        return newEntries;
    }

}
