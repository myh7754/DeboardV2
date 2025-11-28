package org.example.deboardv2.rss.service.Impl;

import com.rometools.rome.feed.synd.SyndEntry;
import org.example.deboardv2.rss.domain.RssPost;
import org.example.deboardv2.rss.service.RssParserStrategy;
import org.jdom2.Element;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class NaverRssParser implements RssParserStrategy {
    @Override
    public boolean supports(String feedUrl) {
        return feedUrl.contains("d2.naver.com");
    }

    @Override
    public String resolve(String url) {
        if (!url.startsWith("http")) {
            url = "https://" + url;
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        // d2.naver.com은 /d2.atom 엔드포인트 사용
        if (url.endsWith("/d2.atom")) {
            return url;
        }

        // d2.naver.com만 입력된 경우
        if (url.equals("https://d2.naver.com")) {
            return url + "/d2.atom";
        }

        return url;
    }
    @Override
    public RssPost parse(SyndEntry entry, String feedUrl) {
        return parse(entry, feedUrl, null);
    }

    @Override
    public RssPost parse(SyndEntry entry, String feedUrl, Element element) {
        return RssPost.builder()
                .title(entry.getTitle())
                .link(entry.getLink())
                .author(extractAuthor(entry))
                .content(extractContent(entry))
                .publishedAt(convertToLocalDateTime(entry.getPublishedDate()))
                .build();
    }

    private String extractAuthor(SyndEntry entry) {
        // Atom 피드에서는 author가 다르게 표현될 수 있음
        if (entry.getAuthor() != null && !entry.getAuthor().isEmpty()) {
            return entry.getAuthor();
        }

        // Atom의 authors 리스트 확인
        if (entry.getAuthors() != null && !entry.getAuthors().isEmpty()) {
            return entry.getAuthors().get(0).getName();
        }

        return "NAVER D2";
    }

    private String extractContent(SyndEntry entry) {
        // Atom 피드의 content 추출
        if (entry.getContents() != null && !entry.getContents().isEmpty()) {
            return entry.getContents().get(0).getValue();
        }

        // description이 있는 경우
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
