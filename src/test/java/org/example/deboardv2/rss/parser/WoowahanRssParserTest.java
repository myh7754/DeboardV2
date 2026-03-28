package org.example.deboardv2.rss.parser;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import org.example.deboardv2.rss.domain.RssPost;
import org.example.deboardv2.rss.parser.Impl.WoowahanRssParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class WoowahanRssParserTest {

    private WoowahanRssParser parser;

    @BeforeEach
    void setUp() {
        parser = new WoowahanRssParser();
    }

    // supports() 테스트

    @Test
    @DisplayName("supports() - techblog.woowahan.com 포함 URL이면 true 반환")
    void supports_woowahan_returnsTrue() {
        assertThat(parser.supports("https://techblog.woowahan.com")).isTrue();
    }

    @Test
    @DisplayName("supports() - techblog.woowahan.com 미포함 URL이면 false 반환")
    void supports_other_returnsFalse() {
        assertThat(parser.supports("https://tech.kakao.com/blog")).isFalse();
    }

    // resolve() 테스트

    @Test
    @DisplayName("resolve() - /feed 없으면 추가")
    void resolve_addsFeed_whenMissing() {
        String result = parser.resolve("https://techblog.woowahan.com");
        assertThat(result).isEqualTo("https://techblog.woowahan.com/feed");
    }

    @Test
    @DisplayName("resolve() - trailing slash 제거 후 /feed 추가")
    void resolve_removesTrailingSlash_thenAddsFeed() {
        String result = parser.resolve("https://techblog.woowahan.com/");
        assertThat(result).isEqualTo("https://techblog.woowahan.com/feed");
    }

    @Test
    @DisplayName("resolve() - 이미 /feed로 끝나면 그대로 반환")
    void resolve_keepsSame_whenAlreadyHasFeed() {
        String result = parser.resolve("https://techblog.woowahan.com/feed");
        assertThat(result).isEqualTo("https://techblog.woowahan.com/feed");
    }

    // parse() 테스트

    @Test
    @DisplayName("parse() - contents 있을 때 첫 번째 값 추출")
    void parse_returnsFirstContent_whenContentsPresent() {
        SyndEntry entry = mock(SyndEntry.class);
        SyndContent content = mock(SyndContent.class);

        given(entry.getTitle()).willReturn("우아한형제들 기술블로그 글");
        given(entry.getLink()).willReturn("https://techblog.woowahan.com/1");
        given(entry.getAuthor()).willReturn("작성자");
        given(entry.getContents()).willReturn(List.of(content));
        given(content.getValue()).willReturn("<p>우아한 본문</p>");
        given(entry.getPublishedDate()).willReturn(new java.util.Date());

        RssPost result = parser.parse(entry);

        assertThat(result.getContent()).isEqualTo("<p>우아한 본문</p>");
    }

    @Test
    @DisplayName("parse() - contents 없을 때 '(내용 없음)' 반환")
    void parse_returnsDefault_whenContentsEmpty() {
        SyndEntry entry = mock(SyndEntry.class);

        given(entry.getTitle()).willReturn("제목");
        given(entry.getLink()).willReturn("https://techblog.woowahan.com/2");
        given(entry.getAuthor()).willReturn("작성자");
        given(entry.getContents()).willReturn(Collections.emptyList());
        given(entry.getPublishedDate()).willReturn(new java.util.Date());

        RssPost result = parser.parse(entry);

        assertThat(result.getContent()).isEqualTo("(내용 없음)");
    }
}
