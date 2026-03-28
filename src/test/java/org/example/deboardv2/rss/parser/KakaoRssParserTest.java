package org.example.deboardv2.rss.parser;

import com.rometools.rome.feed.synd.SyndEntry;
import org.example.deboardv2.rss.domain.RssPost;
import org.example.deboardv2.rss.parser.Impl.KakaoRssParser;
import org.jdom2.Element;
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
class KakaoRssParserTest {

    private KakaoRssParser parser;

    @BeforeEach
    void setUp() {
        parser = new KakaoRssParser();
    }

    // supports() 테스트

    @Test
    @DisplayName("supports() - tech.kakao.com/blog 포함 URL이면 true 반환")
    void supports_kakao_returnsTrue() {
        assertThat(parser.supports("https://tech.kakao.com/blog")).isTrue();
    }

    @Test
    @DisplayName("supports() - tech.kakao.com/blog 미포함 URL이면 false 반환")
    void supports_other_returnsFalse() {
        assertThat(parser.supports("https://techblog.woowahan.com")).isFalse();
    }

    // resolve() 테스트

    @Test
    @DisplayName("resolve() - /feed 없으면 추가")
    void resolve_addsFeed_whenMissing() {
        String result = parser.resolve("https://tech.kakao.com/blog");
        assertThat(result).isEqualTo("https://tech.kakao.com/blog/feed");
    }

    @Test
    @DisplayName("resolve() - trailing slash 제거 후 /feed 추가")
    void resolve_removesTrailingSlash_thenAddsFeed() {
        String result = parser.resolve("https://tech.kakao.com/blog/");
        assertThat(result).isEqualTo("https://tech.kakao.com/blog/feed");
    }

    @Test
    @DisplayName("resolve() - 이미 /feed로 끝나면 그대로 반환")
    void resolve_keepsSame_whenAlreadyHasFeed() {
        String result = parser.resolve("https://tech.kakao.com/blog/feed");
        assertThat(result).isEqualTo("https://tech.kakao.com/blog/feed");
    }

    // parse(entry, element) 테스트

    @Test
    @DisplayName("parse() - thumbnail element 있을 때 <img> 포함 HTML 생성")
    void parse_includesImg_whenThumbnailElementPresent() {
        SyndEntry entry = mock(SyndEntry.class);
        given(entry.getTitle()).willReturn("카카오 기술블로그 글");
        given(entry.getLink()).willReturn("https://tech.kakao.com/blog/1");
        given(entry.getAuthor()).willReturn("kakao");
        given(entry.getPublishedDate()).willReturn(new java.util.Date());
        given(entry.getForeignMarkup()).willReturn(Collections.emptyList());

        // org.jdom2.Element 직접 생성
        Element rawItem = new Element("item");
        Element thumbnail = new Element("thumbnail");
        thumbnail.setText("https://cdn.kakao.com/image.jpg");
        rawItem.addContent(thumbnail);

        RssPost result = parser.parse(entry, rawItem);

        assertThat(result.getContent()).contains("<img src=\"https://cdn.kakao.com/image.jpg\"/>");
        assertThat(result.getContent()).contains("https://tech.kakao.com/blog/1");
        assertThat(result.getContent()).contains("카카오 기술블로그 글");
    }

    @Test
    @DisplayName("parse() - element null이면 이미지 없는 HTML 생성")
    void parse_noImg_whenElementNull() {
        SyndEntry entry = mock(SyndEntry.class);
        given(entry.getTitle()).willReturn("카카오 글");
        given(entry.getLink()).willReturn("https://tech.kakao.com/blog/2");
        given(entry.getAuthor()).willReturn("kakao");
        given(entry.getPublishedDate()).willReturn(new java.util.Date());
        given(entry.getForeignMarkup()).willReturn(Collections.emptyList());

        RssPost result = parser.parse(entry, null);

        assertThat(result.getContent()).doesNotContain("<img");
        assertThat(result.getContent()).contains("https://tech.kakao.com/blog/2");
        assertThat(result.getContent()).contains("카카오 글");
    }
}
