package org.example.deboardv2.post.service.Impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 에디터 본문의 서버측 새니타이즈.
 * 프론트 DOMPurify 는 브라우저에서만 돌기 때문에 이쪽이 실제 방어선이다.
 */
class PostContentSanitizeTest {

    @Test
    @DisplayName("script 태그와 이벤트 핸들러, javascript: 링크를 걷어낸다")
    void 실행가능한_것을_제거한다() {
        String dirty = "<p>정상 문단</p>"
                + "<script>alert('xss')</script>"
                + "<img src=\"https://ex.com/a.png\" onerror=\"alert(1)\">"
                + "<a href=\"javascript:alert(1)\">링크</a>";

        String clean = PostServiceImpl.cleanHtml(dirty);

        assertThat(clean).contains("정상 문단");
        assertThat(clean).doesNotContain("script");
        assertThat(clean).doesNotContain("onerror");
        assertThat(clean).doesNotContain("javascript:");
    }

    @Test
    @DisplayName("에디터가 실제로 내보내는 태그는 살려둔다")
    void 서식은_보존한다() {
        String html = "<h2>제목</h2><p><strong>굵게</strong> <em>기울임</em> <u>밑줄</u> <s>취소선</s></p>"
                + "<ul><li>목록</li></ul><blockquote>인용</blockquote>"
                + "<pre><code>코드</code></pre><hr>"
                + "<a href=\"https://ex.com\" target=\"_blank\" rel=\"noopener noreferrer\">링크</a>";

        String clean = PostServiceImpl.cleanHtml(html);

        assertThat(clean)
                .contains("<h2>", "<strong>", "<em>", "<u>", "<s>", "<li>", "<blockquote>", "<pre>", "<hr>")
                .contains("target=\"_blank\"");
    }

    @Test
    @DisplayName("base64 이미지는 본문에 박히지 못한다 — 이미지는 URL 로만 들어와야 한다")
    void data_URI_이미지는_막는다() {
        String clean = PostServiceImpl.cleanHtml(
                "<p>글</p><img src=\"data:image/png;base64,iVBORw0KGgo=\">");

        assertThat(clean).contains("글");
        assertThat(clean).doesNotContain("base64");
    }

    @Test
    @DisplayName("null 은 그대로 통과시킨다 — 검증은 @NotBlank 가 맡는다")
    void null_처리() {
        assertThat(PostServiceImpl.cleanHtml(null)).isNull();
    }
}
