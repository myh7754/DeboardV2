package org.example.deboardv2.rss.service.Impl;

import com.rometools.rome.feed.synd.SyndEntry;
import org.example.deboardv2.rss.domain.RssPost;
import org.example.deboardv2.rss.service.RssParserStrategy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class TistoryRssParser implements RssParserStrategy {
    @Override
    public boolean supports(String feedUrl) {
        return feedUrl.contains("tistory.com");
    }

    @Override
    public String resolve(String url) {
        if (url.endsWith("/")) { // url이 /로 끝나는지 확인
            url = url.substring(0,url.length()-1); // 만약 /로 끝난다면 마지막/를 잘라냄
        }
        return url.endsWith("/rss") ? url : url + "/rss";
    }

    // 만약 tistory가 아닌경우 content내용이 축약되어 있는 경우 여기서 수정?
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
