package org.example.deboardv2.redis.service;

import java.time.Duration;
import java.util.List;
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

    // Set에 값 추가 및 만료시간 설정 (중복 체크용)
    void addToSet(String key, String value, int i);

    // Set에 특정 값이 있는지 확인
    boolean isMemberOfSet(String key, String value);

    Set<String> getAllFromZSet(String key);
    void addAllToZSet(String key, List<String> value, int maxSize);

    List<Boolean> checkLinksExistence(String key, List<String> links);
}
