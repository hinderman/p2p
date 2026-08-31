package com.project.backend.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/**
 * Caps registration and verification-resend traffic.
 *
 * <p>The per-address cap keeps a mailbox from being flooded; the wider per-IP cap
 * bounds bulk account creation while leaving room for the several people who
 * legitimately share one address, such as an office or a mobile carrier NAT.
 */
@ConfigurationProperties("security.registration.rate-limit")
public record RegistrationRateLimitProperties(
        Duration window,
        int maxAttemptsPerEmail,
        int maxAttemptsPerIp) {

    public RegistrationRateLimitProperties {
        Objects.requireNonNull(window, "The rate-limit window is required");
        if (window.isZero() || window.isNegative() || maxAttemptsPerEmail < 1 || maxAttemptsPerIp < 1) {
            throw new IllegalArgumentException("Registration rate-limit configuration contains an invalid value");
        }
    }
}
