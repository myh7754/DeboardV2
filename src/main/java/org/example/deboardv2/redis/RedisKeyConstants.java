package org.example.deboardv2.redis;

/**
 * Redis 키 상수 정의
 * 분산된 Redis 키 문자열을 중앙화하여 오타/변경 위험 제거
 */
public class RedisKeyConstants {
    // RSS 피드 캐싱
    public static final String RSS_FEED = "rss:feed:";

    // 이메일 인증
    public static final String EMAIL_AUTH = "email:";

    // 토큰 블랙리스트
    public static final String REFRESH_TOKEN = "refresh:";

    // 비로그인 게시글 목록 캐싱
    public static final String POST_PUBLIC_PAGE = "post:public:page:";
    public static final String POST_PUBLIC_COUNT = "post:public:count";

    private RedisKeyConstants() {
        // Utility class - instantiation not allowed
    }
}
