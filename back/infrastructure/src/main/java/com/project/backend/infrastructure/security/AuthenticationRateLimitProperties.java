package com.project.backend.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties("security.authentication.rate-limit")
public record AuthenticationRateLimitProperties(
        Duration window,
        int maxFailuresPerEmail,
        int maxFailuresPerIp) {

    public AuthenticationRateLimitProperties {
        Objects.requireNonNull(window, "The rate-limit window is required");
        if (window.isZero() || window.isNegative() || maxFailuresPerEmail < 1 || maxFailuresPerIp < 1) {
            throw new IllegalArgumentException("Authentication rate-limit configuration contains an invalid value");
        }
    }
}
