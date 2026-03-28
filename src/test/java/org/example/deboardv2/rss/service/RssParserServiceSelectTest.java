package org.example.deboardv2.rss.service;

import org.example.deboardv2.post.repository.PostRepository;
import org.example.deboardv2.redis.service.RedisService;
import org.example.deboardv2.rss.parser.RssParserStrategy;
import org.example.deboardv2.rss.parser.Impl.KakaoRssParser;
import org.example.deboardv2.rss.parser.Impl.NaverRssParser;
import org.example.deboardv2.rss.parser.Impl.TistoryRssParser;
import org.example.deboardv2.rss.parser.Impl.VelogRssParser;
import org.example.deboardv2.rss.parser.Impl.WoowahanRssParser;
import org.example.deboardv2.user.repository.ExternalAuthorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class RssParserServiceSelectTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private ExternalAuthorService externalAuthorService;

    @Mock
    private ExternalAuthorRepository externalAuthorRepository;

    @Mock
    private RedisService redisService;

    private RssParserService rssParserService;

    @BeforeEach
    void setUp() {
        List<RssParserStrategy> parsers = List.of(
                new NaverRssParser(),
                new TistoryRssParser(),
                new VelogRssParser(),
                new KakaoRssParser(),
                new WoowahanRssParser()
        );
        rssParserService = new RssParserService(
                parsers,
                postRepository,
                externalAuthorService,
                externalAuthorRepository,
                redisService
        );
    }

    @Test
    @DisplayName("selectParser() - d2.naver.com URL이면 NaverRssParser 반환")
    void selectParser_returnsNaverParser_forNaverUrl() {
        RssParserStrategy result = rssParserService.selectParser("https://d2.naver.com/d2.atom");
        assertThat(result).isInstanceOf(NaverRssParser.class);
    }

    @Test
    @DisplayName("selectParser() - tistory.com URL이면 TistoryRssParser 반환")
    void selectParser_returnsTistoryParser_forTistoryUrl() {
        RssParserStrategy result = rssParserService.selectParser("https://example.tistory.com/rss");
        assertThat(result).isInstanceOf(TistoryRssParser.class);
    }

    @Test
    @DisplayName("selectParser() - velog.io URL이면 VelogRssParser 반환")
    void selectParser_returnsVelogParser_forVelogUrl() {
        RssParserStrategy result = rssParserService.selectParser("https://velog.io/@devuser");
        assertThat(result).isInstanceOf(VelogRssParser.class);
    }

    @Test
    @DisplayName("selectParser() - tech.kakao.com/blog URL이면 KakaoRssParser 반환")
    void selectParser_returnsKakaoParser_forKakaoUrl() {
        RssParserStrategy result = rssParserService.selectParser("https://tech.kakao.com/blog/feed");
        assertThat(result).isInstanceOf(KakaoRssParser.class);
    }

    @Test
    @DisplayName("selectParser() - techblog.woowahan.com URL이면 WoowahanRssParser 반환")
    void selectParser_returnsWoowahanParser_forWoowahanUrl() {
        RssParserStrategy result = rssParserService.selectParser("https://techblog.woowahan.com/feed");
        assertThat(result).isInstanceOf(WoowahanRssParser.class);
    }

    @Test
    @DisplayName("selectParser() - 지원하지 않는 URL이면 IllegalArgumentException 발생")
    void selectParser_throwsException_forUnsupportedUrl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> rssParserService.selectParser("https://unsupported-blog.example.com")
        );
    }
}
