package org.example.deboardv2.rss.parser.Impl;

import com.rometools.rome.feed.synd.SyndEntry;
import org.example.deboardv2.rss.domain.RssPost;
import org.example.deboardv2.rss.parser.RssParserStrategy;
import org.jdom2.Element;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class MockRssParser implements RssParserStrategy {

    @Override
    public boolean supports(String feedUrl) {
        return feedUrl.contains("localhost");
    }

    @Override
    public String resolve(String url) {
        return url;
    }

    @Override
    public RssPost parse(SyndEntry entry) {
        return parse(entry,null);
    }

    @Override
    public RssPost parse(SyndEntry entry, Element element) {
        return RssPost.builder()
                .title(entry.getTitle())
                .link(entry.getLink())
                .author("MockAuthor")
                .content(entry.getDescription() != null ? entry.getDescription().getValue() : "(내용 없음)")
                .publishedAt(entry.getPublishedDate() != null ?
                        LocalDateTime.ofInstant(entry.getPublishedDate().toInstant(), ZoneId.systemDefault()) :
                        LocalDateTime.now())
                .build();
    }
}
