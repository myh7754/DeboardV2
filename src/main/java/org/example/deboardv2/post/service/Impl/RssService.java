package org.example.deboardv2.post.service.Impl;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import org.example.deboardv2.post.dto.RssPost;
import org.example.deboardv2.post.entity.Post;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.user.entity.ExternalAuthor;
import org.example.deboardv2.user.repository.ExternalAuthorRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RssService {
    private final PostRepository postRepository;
    private final ExternalAuthorRepository externalAuthorRepository;

    // rss url에서 글을 읽어와 post entity로 저장
    public void fetchRssFeed(String feedUrl) throws Exception {
        URL url = new URL(feedUrl);
        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed = input.build(new XmlReader(url));

        List<RssPost> rssPosts = feed.getEntries().stream()
                .map(entry -> RssPost.builder()
                        .title(entry.getTitle())
                        .link(entry.getLink())
                        .author(entry.getAuthor())
                        .content(getDescription(entry))
                        .publishedAt(convertToLocalDateTime(entry.getPublishedDate()))
                        .build())
                .collect(Collectors.toList());

        for (RssPost rssPost : rssPosts) {
            if (postRepository.existsByLink(rssPost.getLink()))
                continue;

            ExternalAuthor externalAuthor = externalAuthorRepository
                    .findByNameAndSourceUrl(rssPost.getAuthor(), feedUrl)
                    .orElseGet(() -> {
                        ExternalAuthor newAuthor = new ExternalAuthor();
                        newAuthor.update(rssPost.getAuthor(),feedUrl);
                        return externalAuthorRepository.save(newAuthor);
                    });
            Post post = Post.fromRss(rssPost.getTitle(), rssPost.getContent(), rssPost.getLink(), rssPost.getPublishedAt(), externalAuthor);
            postRepository.save(post);
        }
    }

    private String getDescription(SyndEntry entry) {
        if (entry.getDescription() != null) {
            return entry.getDescription().getValue();
        }
        return "(내용 없음)";
    }

    private LocalDateTime convertToLocalDateTime(java.util.Date date) {
        if (date == null) return null;
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }
 }
