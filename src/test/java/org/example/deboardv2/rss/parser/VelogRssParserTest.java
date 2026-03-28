package org.example.deboardv2.rss.parser;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import org.example.deboardv2.rss.domain.RssPost;
import org.example.deboardv2.rss.parser.Impl.VelogRssParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class VelogRssParserTest {

    private VelogRssParser parser;

    @BeforeEach
    void setUp() {
        parser = new VelogRssParser();
    }

    // supports() 테스트

    @Test
    @DisplayName("supports() - velog.io 포함 URL이면 true 반환")
    void supports_velog_returnsTrue() {
        assertThat(parser.supports("https://velog.io/@username")).isTrue();
    }

    @Test
    @DisplayName("supports() - velog.io 미포함 URL이면 false 반환")
    void supports_other_returnsFalse() {
        assertThat(parser.supports("https://tistory.com")).isFalse();
    }

    // resolve() 테스트

    @Test
    @DisplayName("resolve() - @username 포함 URL에서 RSS URL 조합")
    void resolve_extractsUsername_andBuildsRssUrl() {
        String result = parser.resolve("https://velog.io/@myuser");
        assertThat(result).isEqualTo("https://v2.velog.io/rss/myuser");
    }

    @Test
    @DisplayName("resolve() - @ 없는 URL이면 null 반환")
    void resolve_returnsNull_whenNoAtSign() {
        String result = parser.resolve("https://velog.io/no-at-sign");
        assertThat(result).isNull();
    }

    // parse() 테스트

    @Test
    @DisplayName("parse() - link에서 @username 추출하여 'velog@{username}' 형식으로 반환")
    void parse_extractsVelogAuthor_fromLink() {
        SyndEntry entry = mock(SyndEntry.class);
        SyndContent description = mock(SyndContent.class);

        given(entry.getTitle()).willReturn("벨로그 글");
        given(entry.getLink()).willReturn("https://velog.io/@devuser/post-title");
        given(entry.getDescription()).willReturn(description);
        given(description.getValue()).willReturn("본문 내용");
        given(entry.getPublishedDate()).willReturn(new java.util.Date());

        RssPost result = parser.parse(entry);

        assertThat(result.getAuthor()).isEqualTo("velog@devuser");
    }

    @Test
    @DisplayName("parse() - link 경로 세그먼트가 @로 시작하지 않으면 'velog@unknown' 반환")
    void parse_returnsVelogUnknown_whenPathSegmentNotAtPrefixed() {
        SyndEntry entry = mock(SyndEntry.class);

        given(entry.getTitle()).willReturn("벨로그 글");
        // 경로 첫 세그먼트가 @로 시작하지 않는 경우
        given(entry.getLink()).willReturn("https://velog.io/rss/academey");
        given(entry.getDescription()).willReturn(null);
        given(entry.getPublishedDate()).willReturn(null);

        RssPost result = parser.parse(entry);

        assertThat(result.getAuthor()).isEqualTo("velog@unknown");
    }
}
