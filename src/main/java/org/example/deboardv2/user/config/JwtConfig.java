package org.example.deboardv2.user.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "custom.jwt")
public class JwtConfig {

    private final Validation validation;
    private final Secret secret;

    @Getter
    @RequiredArgsConstructor
    public static class Validation {
        private final Duration access;
        private final Duration refresh;
    }

    @Getter
    @RequiredArgsConstructor
    public static class Secret {
        private final String appKey;
        private final String originKey;
    }

}
