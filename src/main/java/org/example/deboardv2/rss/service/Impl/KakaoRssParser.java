package org.example.deboardv2.rss.service.Impl;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.rss.domain.RssPost;
import org.example.deboardv2.rss.service.RssParserStrategy;
import org.jdom2.Element;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@Slf4j
public class KakaoRssParser implements RssParserStrategy {
    @Override
    public boolean supports(String feedUrl) {
        boolean contains = feedUrl.contains("tech.kakao.com/blog");
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
        return parse(entry, feedUrl,null);
    }

    @Override
    public RssPost parse(SyndEntry entry, String feedUrl, Element element) {
        String image = extractThumbnail(element);
        return RssPost.builder()
                .title(entry.getTitle())
                .link(entry.getLink())
                .author(entry.getAuthor())
                .content(buildHtmlContent(entry, image))
                .publishedAt(convertToLocalDateTime(entry.getPublishedDate()))
                .build();
    }



    private String buildHtmlContent(SyndEntry entry, String image) {
        log.info(entry.getForeignMarkup().toString());

        String title = entry.getTitle();
        String link = entry.getLink();

        String htmlContent = "<a href=\"" + link + "\">"
                + "<h3>" + title + "</h3>"
                + (image != null ? "<img src=\"" + image + "\"/>" : "")
                + "</a>";
        return htmlContent;
    }

    private String extractThumbnail(Element rawItem) {
        if (rawItem == null) {
            return null;
        }

        Element thumbnail = rawItem.getChild("thumbnail");
        if (thumbnail != null) {
            return thumbnail.getText();
        }

        return null;
    }

    private LocalDateTime convertToLocalDateTime(java.util.Date date) {
        if (date == null) return null;
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }
}
