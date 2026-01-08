package org.example.deboardv2.refactorrss.parser;

import com.rometools.rome.feed.synd.SyndEntry;
import org.example.deboardv2.refactorrss.domain.RssPost;
import org.jdom2.Element;


public interface RssParserStrategy {
    public boolean supports(String feedUrl); // 내가 제공하는 url인지
    public String resolve(String url);
    public RssPost parse(SyndEntry entry); // SyndEntry는 Rss피드의 하나의 Item을 표현하는 객체
    default RssPost parse(SyndEntry entry, Element element) {
        return parse(entry);
    }
}
