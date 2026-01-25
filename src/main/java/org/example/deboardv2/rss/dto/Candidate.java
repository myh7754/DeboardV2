package org.example.deboardv2.rss.dto;

import com.rometools.rome.feed.synd.SyndEntry;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.deboardv2.rss.domain.Feed;

@AllArgsConstructor
@Getter
public class Candidate {
    private Feed feed;
    private SyndEntry entry;
    private String redisKey;
    private FeedFetchResult result;
}
