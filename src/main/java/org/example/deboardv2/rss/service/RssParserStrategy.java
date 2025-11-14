package org.example.deboardv2.rss.service;

import com.rometools.rome.feed.synd.SyndEntry;
import org.example.deboardv2.rss.domain.RssPost;

import java.util.List;

public interface RssParserStrategy {
    public boolean supports(String feedUrl); // 내가 제공하는 url인지
    public String resolve(String url);
    public RssPost parse(SyndEntry entry, String feedUrl); // SyndEntry는 Rss피드의 하나의 Item을 표현하는 객체
}
