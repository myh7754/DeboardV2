package org.example.deboardv2.rss.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.redis.service.RedisService;
import org.example.deboardv2.rss.domain.Feed;
import org.example.deboardv2.user.repository.ExternalAuthorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RssParserServiceNewEntriesTest {

    @Mock private PostRepository postRepository;
    @Mock private ExternalAuthorService externalAuthorService;
    @Mock private ExternalAuthorRepository externalAuthorRepository;
    @Mock private RedisService redisService;

    private RssParserService rssParserService;
    private final Feed feed = Feed.builder().feedUrl("https://a.tistory.com/rss").build();

    @BeforeEach
    void setUp() {
        rssParserService = new RssParserService(List.of(), postRepository,
                externalAuthorService, externalAuthorRepository, redisService);
    }

    private static SyndEntry entry(String link) {
        SyndEntry e = new SyndEntryImpl();
        e.setLink(link);
        return e;
    }

    @Test
    @DisplayName("Redis 캐시(50개 제한)에서 밀려났지만 DB에 이미 있는 링크는 새 글로 보지 않는다")
    void redisMiss_butInDb_isNotNew() {
        List<SyndEntry> entries = List.of(entry("https://a.tistory.com/1"), entry("https://a.tistory.com/2"));
        given(redisService.hasKey(anyString())).willReturn(true);
        given(redisService.checkLinksExistence(anyString(), any())).willReturn(List.of(false, false));
        given(postRepository.findExistingLinksByFeed(any(), any())).willReturn(Set.of("https://a.tistory.com/1"));

        List<SyndEntry> result = rssParserService.extractPostListImprove(feed, entries);

        assertThat(result).extracting(SyndEntry::getLink).containsExactly("https://a.tistory.com/2");
    }

    @Test
    @DisplayName("post.link 컬럼(500자)보다 긴 링크는 저장 대상에서 제외한다")
    void tooLongLink_isSkipped() {
        String longLink = "https://a.tistory.com/entry/" + "%EA%B0%80".repeat(60);
        List<SyndEntry> entries = List.of(entry(longLink), entry("https://a.tistory.com/3"));
        given(redisService.hasKey(anyString())).willReturn(false);
        given(postRepository.findExistingLinksByFeed(any(), any())).willReturn(Set.of());

        List<SyndEntry> result = rssParserService.extractPostListImprove(feed, entries);

        assertThat(result).extracting(SyndEntry::getLink).containsExactly("https://a.tistory.com/3");
    }
}
