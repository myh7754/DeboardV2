package org.example.deboardv2.rss.service.Impl;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.rss.domain.RssPost;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.rss.service.RssParserStrategy;
import org.example.deboardv2.user.entity.ExternalAuthor;
import org.example.deboardv2.user.repository.ExternalAuthorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RssService {
    private final PostRepository postRepository;
    private final ExternalAuthorRepository externalAuthorRepository;
    private final List<RssParserStrategy> parserStrategies;

    // rss url에서 글을 읽어와 post entity로 저장
    @Transactional
    public void fetchRssFeed(String feedUrl) throws Exception {
        URL url = new URL(feedUrl);
        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed = input.build(new XmlReader(url));

        List<SyndEntry> entries = feed.getEntries();
        RssParserStrategy parser = parserStrategies.stream()
                .filter(p -> p.supports(feedUrl))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 블로그입니다"));

        for (SyndEntry entry : entries) {
            RssPost rssPost = parser.parse(entry, feedUrl);
            saveIfNew(rssPost, feedUrl);

        }
    }

    @Transactional
    protected void saveIfNew(RssPost rssPost, String feedUrl) throws Exception {
        if (rssPost == null || rssPost.getLink() == null) {
            return;
        }

        if (postRepository.existsByLink(rssPost.getLink())) {
            return;
        }

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
                rssPost.getLink(),
                rssPost.getPublishedAt(),
                author
        );
        postRepository.save(post);
    }
}
