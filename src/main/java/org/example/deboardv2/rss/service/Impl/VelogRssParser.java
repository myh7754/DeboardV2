package org.example.deboardv2.rss.service.Impl;

import com.rometools.rome.feed.synd.SyndEntry;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.rss.domain.RssPost;
import org.example.deboardv2.rss.service.RssParserStrategy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@Slf4j
public class VelogRssParser implements RssParserStrategy {

    private static final String PRIMARY_RSS_BASE = "https://v2.velog.io/rss/";
    private static final String FALLBACK_RSS_BASE = "https://api.velog.io/rss/";

    @Override
    public boolean supports(String feedUrl) {
        boolean contains = feedUrl.contains("velog.io");
        return contains;
    }

    @Override
    public String resolve(String url) {
        if (!url.startsWith("http")) {
            url = "https://" + url;
        }
        int atIndex = url.indexOf("@");
        if (atIndex == -1) return null;

        String afterAt = url.substring(atIndex + 1);
        String username = afterAt.split("/")[0];

        String resolveUrl = PRIMARY_RSS_BASE + username;
        return resolveUrl;
    }

    @Override
    public RssPost parse(SyndEntry entry, String feedUrl) {
        log.info("link {}", entry.getLink());
        log.info("feedUrl {}", feedUrl);
        return RssPost.builder()
                .title(entry.getTitle())
                .link(entry.getLink())
                .author(entry.getAuthor())
                .content(getDescription(entry))
                .publishedAt(convertToLocalDateTime(entry.getPublishedDate()))
                .build();
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
