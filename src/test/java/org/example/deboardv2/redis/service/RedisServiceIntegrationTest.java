package org.example.deboardv2.redis.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RedisServiceIntegrationTest {

    @Autowired
    private RedisService redisService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String TEST_KEY_PREFIX = "test:redis:";

    @AfterEach
    void tearDown() {
        Set<String> keys = redisTemplate.keys(TEST_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // -----------------------------------------------------------------------
    // setValue / getValue
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("setValue 저장 후 getValue로 동일 값 조회")
    void setValue_그리고_getValue_정상조회() {
        // given
        String key = TEST_KEY_PREFIX + "value";
        String expected = "hello-redis";

        // when
        redisService.setValue(key, expected);
        Object actual = redisService.getValue(key);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("존재하지 않는 키 조회 시 null 반환")
    void getValue_존재하지않는키_null반환() {
        // given
        String key = TEST_KEY_PREFIX + "nonexistent";

        // when
        Object result = redisService.getValue(key);

        // then
        assertThat(result).isNull();
    }

    // -----------------------------------------------------------------------
    // setValueWithExpire
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("setValueWithExpire TTL 만료 후 getValue는 null 반환")
    void setValueWithExpire_만료후_null반환() throws InterruptedException {
        // given
        String key = TEST_KEY_PREFIX + "expire";
        redisService.setValueWithExpire(key, "temporary-value", Duration.ofSeconds(1));

        // when
        Thread.sleep(1500);
        Object result = redisService.getValue(key);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("setValueWithExpire TTL 이내 조회 시 값 반환")
    void setValueWithExpire_만료전_값반환() {
        // given
        String key = TEST_KEY_PREFIX + "expire-before";
        String expected = "still-alive";
        redisService.setValueWithExpire(key, expected, Duration.ofSeconds(10));

        // when
        Object actual = redisService.getValue(key);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    // -----------------------------------------------------------------------
    // checkAndDelete (Lua 스크립트 원자적 처리)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("checkAndDelete - 키 존재 시 true 반환 및 키 삭제")
    void checkAndDelete_키존재시_true반환및삭제() {
        // given
        String key = TEST_KEY_PREFIX + "cad-exists";
        redisService.setValue(key, "some-value");

        // when
        boolean result = redisService.checkAndDelete(key);

        // then
        assertThat(result).isTrue();
        assertThat(redisService.hasKey(key)).isFalse();
    }

    @Test
    @DisplayName("checkAndDelete - 키 없을 때 false 반환")
    void checkAndDelete_키없을때_false반환() {
        // given
        String key = TEST_KEY_PREFIX + "cad-absent";

        // when
        boolean result = redisService.checkAndDelete(key);

        // then
        assertThat(result).isFalse();
    }

    // -----------------------------------------------------------------------
    // addToSet — ZSet maxSize 초과 시 오래된 항목 삭제
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("addToSet maxSize=3 초과 시 오래된 항목 삭제 후 크기 3 유지")
    void addToSet_maxSize초과시_크기유지() throws InterruptedException {
        // given
        String key = TEST_KEY_PREFIX + "zset-add";
        int maxSize = 3;

        // when
        // System.currentTimeMillis()를 score로 사용하므로 호출 사이에 1ms 이상 간격 필요
        redisService.addToSet(key, "link-1", maxSize);
        Thread.sleep(2);
        redisService.addToSet(key, "link-2", maxSize);
        Thread.sleep(2);
        redisService.addToSet(key, "link-3", maxSize);
        Thread.sleep(2);
        redisService.addToSet(key, "link-4", maxSize);
        Thread.sleep(2);
        redisService.addToSet(key, "link-5", maxSize);

        // then
        Long size = redisTemplate.opsForZSet().zCard(key);
        assertThat(size).isEqualTo(maxSize);

        // 가장 최신 3개가 남아 있어야 함
        assertThat(redisService.isMemberOfSet(key, "link-3")).isTrue();
        assertThat(redisService.isMemberOfSet(key, "link-4")).isTrue();
        assertThat(redisService.isMemberOfSet(key, "link-5")).isTrue();
    }

    @Test
    @DisplayName("addToSet maxSize 이하 추가 시 모든 항목 유지")
    void addToSet_maxSize이하_모두유지() throws InterruptedException {
        // given
        String key = TEST_KEY_PREFIX + "zset-add-under";
        int maxSize = 5;

        // when
        redisService.addToSet(key, "link-a", maxSize);
        Thread.sleep(2);
        redisService.addToSet(key, "link-b", maxSize);
        Thread.sleep(2);
        redisService.addToSet(key, "link-c", maxSize);

        // then
        Long size = redisTemplate.opsForZSet().zCard(key);
        assertThat(size).isEqualTo(3);
        assertThat(redisService.isMemberOfSet(key, "link-a")).isTrue();
        assertThat(redisService.isMemberOfSet(key, "link-b")).isTrue();
        assertThat(redisService.isMemberOfSet(key, "link-c")).isTrue();
    }

    // -----------------------------------------------------------------------
    // addAllToZSet — 배치 추가 후 maxSize 유지
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("addAllToZSet 10개 추가 maxSize=5 설정 시 ZSet 크기 5 유지")
    void addAllToZSet_maxSize초과시_크기유지() {
        // given
        String key = TEST_KEY_PREFIX + "zset-batch";
        int maxSize = 5;
        List<String> values = List.of(
                "batch-link-1", "batch-link-2", "batch-link-3", "batch-link-4", "batch-link-5",
                "batch-link-6", "batch-link-7", "batch-link-8", "batch-link-9", "batch-link-10"
        );

        // when
        redisService.addAllToZSet(key, values, maxSize);

        // then
        Long size = redisTemplate.opsForZSet().zCard(key);
        assertThat(size).isEqualTo(maxSize);
    }

    @Test
    @DisplayName("addAllToZSet 빈 리스트 전달 시 ZSet에 변화 없음")
    void addAllToZSet_빈리스트_변화없음() {
        // given
        String key = TEST_KEY_PREFIX + "zset-batch-empty";

        // when
        redisService.addAllToZSet(key, List.of(), 5);

        // then
        Long size = redisTemplate.opsForZSet().zCard(key);
        assertThat(size).isNull(); // 키 자체가 생성되지 않음
    }

    // -----------------------------------------------------------------------
    // checkLinksExistence — addAllToZSet(raw bytes)으로 넣은 값 대상
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("checkLinksExistence 존재/비존재 혼합 조회 시 Boolean 리스트 정확히 반환")
    void checkLinksExistence_존재비존재혼합_boolean리스트반환() {
        // given
        String key = TEST_KEY_PREFIX + "zset-check";
        List<String> storedLinks = List.of("link-alpha", "link-beta", "link-gamma");
        // addAllToZSet은 raw bytes로 ZSet에 저장하므로 checkLinksExistence(raw bytes 조회)와 일치
        redisService.addAllToZSet(key, storedLinks, 10);

        List<String> queryLinks = List.of("link-alpha", "nonexistent-link", "link-gamma");

        // when
        List<Boolean> result = redisService.checkLinksExistence(key, queryLinks);

        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isTrue();   // link-alpha 존재
        assertThat(result.get(1)).isFalse();  // nonexistent-link 비존재
        assertThat(result.get(2)).isTrue();   // link-gamma 존재
    }

    @Test
    @DisplayName("checkLinksExistence 빈 리스트 조회 시 빈 리스트 반환")
    void checkLinksExistence_빈리스트_빈리스트반환() {
        // given
        String key = TEST_KEY_PREFIX + "zset-check-empty";

        // when
        List<Boolean> result = redisService.checkLinksExistence(key, List.of());

        // then
        assertThat(result).isEmpty();
    }
}
