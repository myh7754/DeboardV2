package org.example.deboardv2.rss.service.Impl;

import com.rometools.rome.feed.synd.SyndEntry;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.rss.domain.RssPost;
import org.example.deboardv2.rss.service.RssParserStrategy;
import org.jdom2.Element;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@Slf4j
public class WoowahanRssParser implements RssParserStrategy {
    @Override
    public boolean supports(String feedUrl) {
        boolean contains = feedUrl.contains("techblog.woowahan.com");
        log.info("contains {}", contains);
        return contains;
    }

    @Override
    public String resolve(String url) {
        if (url.endsWith("/")) { // url이 /로 끝나는지 확인
            url = url.substring(0,url.length()-1); // 만약 /로 끝난다면 마지막/를 잘라냄
        }
        return url.endsWith("/feed") ? url : url + "/feed";
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

    @Override
    public RssPost parse(SyndEntry entry, String feedUrl, Element element) {
        return RssParserStrategy.super.parse(entry, feedUrl, element);
    }

    private String getDescription(SyndEntry entry) {
        if (entry.getContents() != null && !entry.getContents().isEmpty()) {
            return entry.getContents().get(0).getValue();
        }
        return "(내용 없음)";
    }

    private LocalDateTime convertToLocalDateTime(java.util.Date date) {
        if (date == null) return null;
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }
}
