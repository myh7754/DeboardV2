package org.example.deboardv2.rss.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SAXBuilder;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.rss.domain.RssPost;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.sax.XMLReaders;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RssFetchService {
    private final HttpClient httpClient;

    // 외부 네트워크 I/O SyndFeed(Rss피드의 게시글 정보) 가져오기
    // List<SyndEntry> entries = SyndFeed.getEntries() : 피드의 게시글들 목록
    public SyndFeed fetchSyndFeed(String feedUrl) throws Exception {
        long startTime = System.currentTimeMillis();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(feedUrl))
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofByteArray()  // InputStream 대신 byte[] 사용
        );
        long endTime = System.currentTimeMillis();
        log.info("네트워크를 불러오는 시간 {} : {}ms", feedUrl ,(endTime - startTime));
        try (ByteArrayInputStream bais = new ByteArrayInputStream(response.body());
            XmlReader reader = new XmlReader(bais)) {
            SyndFeed feed = new SyndFeedInput().build(reader);
            return feed;
        }

//        URL url = new URL(feedUrl);
//        SyndFeedInput input = new SyndFeedInput();
//        input.setAllowDoctypes(true);
//        input.setPreserveWireFeed(true);
//
//       XmlReader reader = new XmlReader(url);
//       log.info("reader {}", reader.toString());
//       return input.build(reader);

    }

    // 외부 네트워크 I/O
    // 원본 xml element 맵 생성, Rome으로 자동으로 파싱되지 않는 부분 가져오기
    public Map<String, Element> buildRawElement(String feedUrl) throws Exception {
        URL url = new URL(feedUrl);
        SAXBuilder saxBuilder = new SAXBuilder(XMLReaders.DTDVALIDATING);
        Document document = saxBuilder.build(url);
        Element channel = document.getRootElement().getChild("rss");
        List<Element> items = channel.getChildren("item");

        return items.stream()
                .collect(Collectors.toMap(
                        item -> item.getChildText("link"),
                        item -> item
                ));

    }

    public List<RssPost> fetchNewRssPost(SyndFeed feed) throws  Exception {
        List<SyndEntry> entries = feed.getEntries();
        List<String> entriesLinks = entries.stream()
                .map(SyndEntry::getLink)
                .collect(Collectors.toList());
        return null;
    }
}
