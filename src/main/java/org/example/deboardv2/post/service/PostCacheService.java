package org.example.deboardv2.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.post.dto.PostDetailResponse;
import org.example.deboardv2.post.dto.PostPageCacheDto;
import org.example.deboardv2.post.repository.PostCustomRepository;
import org.example.deboardv2.redis.RedisKeyConstants;
import org.example.deboardv2.redis.service.RedisService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 비로그인 첫 페이지 캐시 관리 (SWR 패턴).
 *
 * - 데이터 키 (hard TTL 70s): 실제 게시글 목록
 * - 신호 키  (soft TTL 50s): 갱신 필요 여부를 나타내는 플래그
 *
 * 신호 키가 만료되면 다음 요청이 비동기 갱신을 트리거한다.
 * 데이터 키는 비동기 갱신이 완료될 때까지 stale 상태로 제공된다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostCacheService {

    private final PostCustomRepository postCustomRepository;
    private final RedisService redisService;

    private static final Duration DATA_TTL  = Duration.ofSeconds(70);
    private static final Duration STALE_TTL = Duration.ofSeconds(50);

    public PostPageCacheDto get(String cacheKey) {
        Object cached = redisService.getValue(cacheKey);
        return cached instanceof PostPageCacheDto dto ? dto : null;
    }

    public boolean isStale(String cacheKey) {
        return !redisService.hasKey(cacheKey + RedisKeyConstants.STALE_SUFFIX);
    }

    @Transactional(readOnly = true)
    public PostPageCacheDto refreshSync(String cacheKey, Pageable pageable) {
        return fetchAndStore(cacheKey, pageable);
    }

    @Async("cacheTaskExecutor")
    @Transactional(readOnly = true)
    public void refreshAsync(String cacheKey, Pageable pageable) {
        redisService.setValueWithExpire(cacheKey + RedisKeyConstants.STALE_SUFFIX, "1", STALE_TTL);
        fetchAndStore(cacheKey, pageable);
        log.debug("비로그인 첫 페이지 캐시 비동기 갱신 완료: {}", cacheKey);
    }

    public void evict(String cacheKey) {
        redisService.deleteValue(cacheKey);
        redisService.deleteValue(cacheKey + RedisKeyConstants.STALE_SUFFIX);
    }

    @Transactional(readOnly = true)
    public long getCachedPublicCount() {
        Object cached = redisService.getValue(RedisKeyConstants.POST_PUBLIC_COUNT);
        if (cached instanceof Number n) {
            return n.longValue();
        }
        return refreshPublicCount();
    }

    private long refreshPublicCount() {
        long count = postCustomRepository.countPublic();
        redisService.setValueWithExpire(RedisKeyConstants.POST_PUBLIC_COUNT, count, DATA_TTL);
        return count;
    }

    public void evictPublicCount() {
        redisService.deleteValue(RedisKeyConstants.POST_PUBLIC_COUNT);
    }

    private PostPageCacheDto fetchAndStore(String cacheKey, Pageable pageable) {
        Page<PostDetailResponse> result = postCustomRepository.findAll(pageable);
        PostPageCacheDto dto = new PostPageCacheDto(result.getContent(), result.getTotalElements());

        redisService.setValueWithExpire(cacheKey, dto, DATA_TTL);
        redisService.setValueWithExpire(cacheKey + RedisKeyConstants.STALE_SUFFIX, "1", STALE_TTL);

        return dto;
    }
}
