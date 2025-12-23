package org.example.deboardv2.redis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

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
}
