package com.project.backend.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "app.payment-proof.scanner.clamav")
public record ClamAvProperties(String host, int port, Duration timeout) {
    public ClamAvProperties {
        if (host == null || host.isBlank()) throw new IllegalArgumentException("The ClamAV host is required");
        if (port < 1 || port > 65_535) throw new IllegalArgumentException("The ClamAV port is invalid");
        Objects.requireNonNull(timeout, "The ClamAV timeout is required");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("The ClamAV timeout must be positive");
    }
}
