package org.example.deboardv2.rss.parser;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import org.example.deboardv2.rss.domain.RssPost;
import org.example.deboardv2.rss.parser.Impl.TistoryRssParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class TistoryRssParserTest {

    private TistoryRssParser parser;

    @BeforeEach
    void setUp() {
        parser = new TistoryRssParser();
    }

    // supports() 테스트

    @Test
    @DisplayName("supports() - tistory.com 포함 URL이면 true 반환")
    void supports_tistory_returnsTrue() {
        assertThat(parser.supports("https://example.tistory.com")).isTrue();
    }

    @Test
    @DisplayName("supports() - tistory.com 미포함 URL이면 false 반환")
    void supports_other_returnsFalse() {
        assertThat(parser.supports("https://velog.io/@user")).isFalse();
    }

    // resolve() 테스트

    @Test
    @DisplayName("resolve() - /rss 없으면 추가")
    void resolve_addsRss_whenMissing() {
        String result = parser.resolve("https://example.tistory.com");
        assertThat(result).isEqualTo("https://example.tistory.com/rss");
    }

    @Test
    @DisplayName("resolve() - 이미 /rss로 끝나면 그대로 반환")
    void resolve_keepsSame_whenAlreadyHasRss() {
        String result = parser.resolve("https://example.tistory.com/rss");
        assertThat(result).isEqualTo("https://example.tistory.com/rss");
    }

    @Test
    @DisplayName("resolve() - trailing slash 제거 후 /rss 추가")
    void resolve_removesTrailingSlash_thenAddsRss() {
        String result = parser.resolve("https://example.tistory.com/");
        assertThat(result).isEqualTo("https://example.tistory.com/rss");
    }

    // parse() 테스트

    @Test
    @DisplayName("parse() - description 있을 때 content 정상 추출")
    void parse_returnsDescription_whenPresent() {
        SyndEntry entry = mock(SyndEntry.class);
        SyndContent description = mock(SyndContent.class);

        given(entry.getTitle()).willReturn("티스토리 글 제목");
        given(entry.getLink()).willReturn("https://example.tistory.com/1");
        given(entry.getAuthor()).willReturn("작성자");
        given(entry.getDescription()).willReturn(description);
        given(description.getValue()).willReturn("<p>티스토리 본문</p>");
        given(entry.getPublishedDate()).willReturn(new java.util.Date());

        RssPost result = parser.parse(entry);

        assertThat(result.getContent()).isEqualTo("<p>티스토리 본문</p>");
    }

    @Test
    @DisplayName("parse() - description null이면 '(내용 없음)' 반환")
    void parse_returnsDefault_whenDescriptionNull() {
        SyndEntry entry = mock(SyndEntry.class);

        given(entry.getTitle()).willReturn("제목");
        given(entry.getLink()).willReturn("https://example.tistory.com/2");
        given(entry.getAuthor()).willReturn("작성자");
        given(entry.getDescription()).willReturn(null);
        given(entry.getPublishedDate()).willReturn(new java.util.Date());

        RssPost result = parser.parse(entry);

        assertThat(result.getContent()).isEqualTo("(내용 없음)");
    }

    @Test
    @DisplayName("parse() - publishedDate null이면 publishedAt null 반환")
    void parse_returnsNullPublishedAt_whenPublishedDateNull() {
        SyndEntry entry = mock(SyndEntry.class);

        given(entry.getTitle()).willReturn("제목");
        given(entry.getLink()).willReturn("https://example.tistory.com/3");
        given(entry.getAuthor()).willReturn("작성자");
        given(entry.getDescription()).willReturn(null);
        given(entry.getPublishedDate()).willReturn(null);

        RssPost result = parser.parse(entry);

        assertThat(result.getPublishedAt()).isNull();
    }
}
