package org.example.deboardv2.system.exception;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;



@Component
public class ErrorCache {
    private final Cache<String, ErrorInfo> cache = Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.MINUTES)
            .build();
    
    // 중복된 에러인지 검사
    public boolean isDuplicate(Exception e, String traceId) {
        String key = generateKey(e);
        ErrorInfo info = cache.getIfPresent(key);
        if (info == null) {
            cache.put(key, new ErrorInfo(traceId));
            return false;
        } else {
            info.increment();
            return true;
        }
    }

    public ErrorInfo getErrorInfo(Exception e) {
        return cache.getIfPresent(generateKey(e));
    }

    // 에러 스택트레이스를 키로 변경
    private String generateKey(Exception e) {
        return e.getClass().getName() + ":" + e.getMessage() + ":" + Arrays.toString(e.getStackTrace());
    }

    @Getter
    public static class ErrorInfo {
        private final String firstTraceId;
        private final AtomicInteger count = new AtomicInteger(1);

        public ErrorInfo(String firstTraceId) {
            this.firstTraceId = firstTraceId;
        }

        public void increment() {
            count.incrementAndGet();
        }
    }
}