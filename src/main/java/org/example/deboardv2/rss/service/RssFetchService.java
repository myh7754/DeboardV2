package org.example.deboardv2.rss.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SAXBuilder;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.rss.domain.RssPost;
import org.example.deboardv2.rss.dto.RssFeedData;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RssFetchService {
    private final HttpClient httpClient;

    // 외부 네트워크 I/O SyndFeed(Rss피드의 게시글 정보) 가져오기
    // List<SyndEntry> entries = SyndFeed.getEntries() : 피드의 게시글들 목록
    public RssFeedData fetchRssData(String feedUrl) throws Exception {
        long startTime = System.currentTimeMillis();

        // 1. 네트워크 요청 (기존 로직 유지)
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(feedUrl))
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofByteArray()
        );

        long endTime = System.currentTimeMillis();
        log.info("네트워크 소요 시간 {} : {}ms", feedUrl, (endTime - startTime));

        try (ByteArrayInputStream bais = new ByteArrayInputStream(response.body())) {
            SAXBuilder saxBuilder = new SAXBuilder(XMLReaders.NONVALIDATING);
            // DTD 검증 없이 빠르게 하려면 false 로 설정 가능
            // saxBuilder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            Document document = saxBuilder.build(bais);
            // 3. JDOM Document를 Rome에 주입하여 SyndFeed 생성
            SyndFeed feed = new SyndFeedInput().build(document);
            // 4. Raw Element Map 생성 (RSS vs Atom 분기 처리)
            Map<String, Element> rawElementMap = extractRawElements(document);
            return new RssFeedData(feed, rawElementMap);
        }
    }

    public CompletableFuture<RssFeedData> fetchRssDataAsync(String feedUrl) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(feedUrl))
                .GET()
                .build();

        // sendAsync는 내부적으로 논블로킹으로 동작하며 피닝을 유발하지 않습니다.
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(response.body())) {
                        SAXBuilder saxBuilder = new SAXBuilder(XMLReaders.NONVALIDATING);
                        Document document = saxBuilder.build(bais);
                        SyndFeed feed = new SyndFeedInput().build(document);
                        Map<String, Element> rawElementMap = extractRawElements(document);
                        return new RssFeedData(feed, rawElementMap);
                    } catch (Exception e) {
                        log.error("rss.parse.failed feedUrl={}", feedUrl, e);
                        throw new RuntimeException(e);
                    }
                });
    }

    private Map<String,  Element> extractRawElements(Document document) {
        // xml의 최상위 태그를 가져온다. (<rss> or <feed> 등)
        Element root = document.getRootElement();
        List<Element> items;

        if("rss".equalsIgnoreCase(root.getName())) {
            Element channel = root.getChild("channel");
            if (channel != null) {
                items = channel.getChildren("item");
            } else {
                items = Collections.emptyList();
            }
        // atom 포멧 인경우
        } else if ("feed".equalsIgnoreCase(root.getName())) {
            // Atom: <feed><entry>...</entry></feed>
            items = root.getChildren("entry", root.getNamespace());
        } else {
            // 알 수 없는 포맷
            items = Collections.emptyList();
        }

        // Link를 키로 매핑 (중복 키 방지 로직 추가)
        return items.stream()
                .collect(Collectors.toMap(
                        this::extractLinkFromElement,
                        element -> element,
                        (existing, replacement) -> existing // 중복된 링크가 있다면 기존 것 유지
                ));
    }

    private String extractLinkFromElement(Element element) {
        String link = element.getChildText("link");

        if (link == null) {
            Element atomLink = element.getChild("link", element.getNamespace());
            if (atomLink != null) {
                // 태그 내부 텍스트가 아니라 'href'라는 속성의 값을 가져옵니다.
                link = atomLink.getAttributeValue("href");
            }
        }

        return link != null ? link.trim() : "";
    }


}
