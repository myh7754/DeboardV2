package org.example.deboardv2.rss.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.rss.parser.RssParserStrategy;

@Getter
@AllArgsConstructor
public class FeedFetchResult {
    private Feed feed;
    private RssFeedData rssFeedData;
    private RssParserStrategy rssParserStrategy;
}
