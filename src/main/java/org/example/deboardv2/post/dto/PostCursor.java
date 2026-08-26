package org.example.deboardv2.post.dto;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

// keyset 페이지네이션 커서: (createdAt, id) 튜플을 opaque 토큰으로 인코드/디코드
public record PostCursor(LocalDateTime createdAt, Long id) {

    public String encode() {
        long epochMilli = createdAt.toInstant(ZoneOffset.UTC).toEpochMilli();
        String raw = epochMilli + "_" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static PostCursor decode(String cursor) {
        String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        String[] parts = raw.split("_", 2);
        LocalDateTime createdAt = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(Long.parseLong(parts[0])), ZoneOffset.UTC);
        return new PostCursor(createdAt, Long.parseLong(parts[1]));
    }
}
