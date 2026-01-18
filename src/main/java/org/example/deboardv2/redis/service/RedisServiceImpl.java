package org.example.deboardv2.redis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RedisServiceImpl implements RedisService {
    private final RedisTemplate<String, Object> redisTemplate;

    // (공식처럼 자주 쓰는 코드) - ValueOperations 가져오기
    private ValueOperations<String, Object> valueOps() {
        return redisTemplate.opsForValue();
    }

    // 값 저장
    // SET key value
    @Override
    public void setValue(String key, Object value) {
        valueOps().set(key, value);
    }

    // 값 꺼내기
    // GET key
    @Override
    public Object getValue(String key) {
        return valueOps().get(key);
    }

    // 키 존재 여부 확인
    // EXISTS key
    @Override
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // 값 삭제
    // DEL key
    @Override
    public void deleteValue(String key) {
        redisTemplate.delete(key);
    }

    // 값 저장 (만료 시간 설정)
    // EXPIRE key sec
    // key : 저장할 Redis 키 이름
    // value : 저장할 값
    // timeout : 만료 시간 숫자
    // unit : 시간 단위 TimeUnit.SECONDS, TimeUnit.MINUTES 등
    @Override
    public void setValueWithExpire(String key, Object value, Duration expireTime) {
        valueOps().set(key, value,expireTime);
    }

    @Override
    public void addToSet(String key, String value, int maxSize) {
        redisTemplate.opsForZSet().add(key, value, System.currentTimeMillis());
        Long count = redisTemplate.opsForZSet().zCard(key);
//      주머니(Set) 크기가 maxSize를 넘어가면 가장 오래된 것 하나만 삭제
        if (count != null && count > maxSize) {
            // 0번째(가장 옛날 것)부터 (현재개수 - 최대개수 - 1)번째까지 삭제
            redisTemplate.opsForZSet().removeRange(key, 0, count - maxSize - 1);
        }
    }

    @Override
    public boolean isMemberOfSet(String key, String value) {
        Double score = redisTemplate.opsForZSet().score(key, value);
        return score != null;
    }

    @Override
    public Set<String> getAllFromZSet(String key) {
        Set<Object> members = redisTemplate.opsForZSet().range(key, 0, -1);
        if (members == null) return Collections.emptySet();
        return members.stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Override
    public void addAllToZSet(String key, List<String> values, int maxSize) {
        if (values == null || values.isEmpty()) return;

        byte[] rawKey = key.getBytes();
        long now = System.currentTimeMillis();

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String value : values) {
                connection.zAdd(rawKey, (double) now, value.getBytes());
            }
            // 개수 제한 로직
            connection.zRemRange(rawKey, 0, -(maxSize + 1));

            return null;
        });
    }

    @Override
    public List<Boolean> checkLinksExistence(String key, List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String value : values) {
                // ZSet에서 해당 value의 score를 조회 (있으면 score 반환, 없으면 null)
                connection.zScore(key.getBytes(), value.getBytes());
            }
            return null;
        });

        // 응답 결과를 Boolean 리스트로 변환 (null이 아니면 존재하는 것)
        return results.stream()
                .map(result -> result != null)
                .collect(Collectors.toList());
    }
}
