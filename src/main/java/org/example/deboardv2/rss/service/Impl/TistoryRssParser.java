package org.example.deboardv2.rss.service.Impl;

import com.rometools.rome.feed.synd.SyndEntry;
import org.example.deboardv2.rss.domain.RssPost;
import org.example.deboardv2.rss.service.RssParserStrategy;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public class TistoryRssParser implements RssParserStrategy {
    @Override
    public boolean supports(String feedUrl) {
        return feedUrl.contains("tistory.com");
    }

    @Override
    public RssPost parse(SyndEntry entry, String feedUrl) {
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
