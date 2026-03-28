package org.example.deboardv2.rss.parser;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import org.example.deboardv2.rss.domain.RssPost;
import org.example.deboardv2.rss.parser.Impl.NaverRssParser;
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
class NaverRssParserTest {

    private NaverRssParser parser;

    @BeforeEach
    void setUp() {
        parser = new NaverRssParser();
    }

    // supports() 테스트

    @Test
    @DisplayName("supports() - d2.naver.com 포함 URL이면 true 반환")
    void supports_d2naver_returnsTrue() {
        assertThat(parser.supports("https://d2.naver.com/helloworld")).isTrue();
    }

    @Test
    @DisplayName("supports() - d2.naver.com 미포함 URL이면 false 반환")
    void supports_other_returnsFalse() {
        assertThat(parser.supports("https://techblog.woowahan.com")).isFalse();
        assertThat(parser.supports("https://velog.io/@user")).isFalse();
    }

    // resolve() 테스트

    @Test
    @DisplayName("resolve() - /d2.atom 없는 d2.naver.com URL에 /d2.atom 추가")
    void resolve_addsD2Atom_whenMissing() {
        String result = parser.resolve("https://d2.naver.com");
        assertThat(result).isEqualTo("https://d2.naver.com/d2.atom");
    }

    @Test
    @DisplayName("resolve() - 이미 /d2.atom으로 끝나면 그대로 반환")
    void resolve_keepsSame_whenAlreadyHasD2Atom() {
        String result = parser.resolve("https://d2.naver.com/d2.atom");
        assertThat(result).isEqualTo("https://d2.naver.com/d2.atom");
    }

    @Test
    @DisplayName("resolve() - trailing slash 제거 후 /d2.atom 추가")
    void resolve_removesTrailingSlash_thenAddsD2Atom() {
        String result = parser.resolve("https://d2.naver.com/");
        assertThat(result).isEqualTo("https://d2.naver.com/d2.atom");
    }

    // parse() 테스트

    @Test
    @DisplayName("parse() - getContents() 있을 때 첫 번째 content 값 반환")
    void parse_returnsFirstContent_whenContentsPresent() {
        SyndEntry entry = mock(SyndEntry.class);
        SyndContent content = mock(SyndContent.class);

        given(entry.getTitle()).willReturn("네이버 D2 글");
        given(entry.getLink()).willReturn("https://d2.naver.com/helloworld/1");
        given(entry.getAuthor()).willReturn("홍길동");
        given(entry.getContents()).willReturn(List.of(content));
        given(content.getValue()).willReturn("<p>본문 내용</p>");
        given(entry.getPublishedDate()).willReturn(new java.util.Date());

        RssPost result = parser.parse(entry);

        assertThat(result.getContent()).isEqualTo("<p>본문 내용</p>");
    }

    @Test
    @DisplayName("parse() - author가 null이고 authors 리스트도 없으면 'NAVER D2' 폴백 반환")
    void parse_returnsNaverD2_whenAuthorAbsent() {
        SyndEntry entry = mock(SyndEntry.class);

        given(entry.getTitle()).willReturn("제목");
        given(entry.getLink()).willReturn("https://d2.naver.com/helloworld/2");
        given(entry.getAuthor()).willReturn(null);
        given(entry.getAuthors()).willReturn(Collections.emptyList());
        given(entry.getContents()).willReturn(Collections.emptyList());
        given(entry.getDescription()).willReturn(null);
        given(entry.getPublishedDate()).willReturn(new java.util.Date());

        RssPost result = parser.parse(entry);

        assertThat(result.getAuthor()).isEqualTo("NAVER D2");
    }

    @Test
    @DisplayName("parse() - publishedDate가 null이면 publishedAt null 반환")
    void parse_returnsNullPublishedAt_whenPublishedDateNull() {
        SyndEntry entry = mock(SyndEntry.class);

        given(entry.getTitle()).willReturn("제목");
        given(entry.getLink()).willReturn("https://d2.naver.com/helloworld/3");
        given(entry.getAuthor()).willReturn("작성자");
        given(entry.getContents()).willReturn(Collections.emptyList());
        given(entry.getDescription()).willReturn(null);
        given(entry.getPublishedDate()).willReturn(null);

        RssPost result = parser.parse(entry);

        assertThat(result.getPublishedAt()).isNull();
    }
}
