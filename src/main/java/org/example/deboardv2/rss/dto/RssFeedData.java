package org.example.deboardv2.rss.dto;

import com.rometools.rome.feed.synd.SyndFeed;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jdom2.Element;

import java.util.Map;

@Getter
@AllArgsConstructor
public class RssFeedData {
    private SyndFeed syndFeed;
    // Link를 Key로 하여 Raw Element를 빠르게 찾기 위한 맵
    private Map<String, Element> rawElementMap;
}
