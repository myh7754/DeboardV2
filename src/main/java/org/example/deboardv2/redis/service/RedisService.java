package org.example.deboardv2.redis.service;

import java.time.Duration;
import java.util.Set;

public interface RedisService {
    // 값 저장
    // SET key value
    void setValue(String key, Object value);

    // 값 꺼내기
    // GET key
    Object getValue(String key);

    // 키 존재 여부 확인
    // EXISTS key
    boolean hasKey(String key);

    // 값 삭제
    // DEL key
    void deleteValue(String key);

    // 값 저장 (만료 시간 설정)
    // EXPIRE key sec
    // key : 저장할 Redis 키 이름
    // value : 저장할 값
    // timeout : 만료 시간 숫자
    // unit : 시간 단위 TimeUnit.SECONDS, TimeUnit.MINUTES 등
    void setValueWithExpire(String key, Object value, Duration expireTime);

}
