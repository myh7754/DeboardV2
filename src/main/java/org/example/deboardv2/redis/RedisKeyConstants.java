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

    // 로그인 사용자 구독 피드 ID 목록 / 비공개글 수 캐싱
    public static final String PRIVATE_FEED_IDS = "post:private:feed_ids:";
    public static final String PRIVATE_POST_COUNT = "post:private:count:";

    // SWR(Stale-While-Revalidate) 신호 키 접미사
    public static final String STALE_SUFFIX = ":stale";

    private RedisKeyConstants() {
        // Utility class - instantiation not allowed
    }
}
