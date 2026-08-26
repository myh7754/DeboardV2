package org.example.deboardv2.post.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

// 목록 응답 공통 형태 — offset/keyset 두 경로가 같은 구조를 반환해 프론트 파서를 하나로 유지
// keyset 경로는 totalPages/number를 채우지 않음(null → JSON에서 생략)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostPageResponse(
        List<PostDetailResponse> content,
        String nextCursor,
        boolean hasNext,
        Integer totalPages,
        Integer number
) {
    public static PostPageResponse ofCursor(List<PostDetailResponse> content, boolean hasNext) {
        return new PostPageResponse(content, nextCursorOf(content), hasNext, null, null);
    }

    // offset 경로도 커서를 발급 — URL 직접 점프로 진입한 뒤 keyset으로 이어갈 수 있게 함
    public static PostPageResponse ofOffset(List<PostDetailResponse> content, int totalPages, int number) {
        return new PostPageResponse(content, nextCursorOf(content), number + 1 < totalPages, totalPages, number);
    }

    // 목록 마지막 글의 (createdAt, id) — 다음 요청이 이어붙일 지점
    private static String nextCursorOf(List<PostDetailResponse> content) {
        if (content.isEmpty()) return null;
        PostDetailResponse last = content.get(content.size() - 1);
        return new PostCursor(last.getCreatedAt(), last.getId()).encode();
    }
}
