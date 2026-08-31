package com.project.backend.infrastructure.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

/** Configuration shared by the encrypted outbox publisher and SMTP delivery adapter. */
@ConfigurationProperties("app.email")
public record OutboundEmailProperties(
        boolean enabled,
        String from,
        String senderName,
        URI publicBaseUrl,
        String onboardingPath,
        String verificationPath,
        Duration pollDelay,
        int maxAttempts,
        String outboxEncryptionKey) {

    public OutboundEmailProperties {
        Objects.requireNonNull(from, "The email sender is required");
        Objects.requireNonNull(senderName, "The email sender name is required");
        Objects.requireNonNull(publicBaseUrl, "The public application URL is required");
        Objects.requireNonNull(onboardingPath, "The onboarding path is required");
        Objects.requireNonNull(verificationPath, "The account verification path is required");
        Objects.requireNonNull(pollDelay, "The email polling delay is required");
        Objects.requireNonNull(outboxEncryptionKey, "The email outbox encryption key is required");
        if (from.isBlank() || senderName.isBlank() || publicBaseUrl.getScheme() == null
                || !("http".equalsIgnoreCase(publicBaseUrl.getScheme()) || "https".equalsIgnoreCase(publicBaseUrl.getScheme()))
                || publicBaseUrl.getHost() == null || !onboardingPath.startsWith("/") || !verificationPath.startsWith("/")
                || pollDelay.isNegative() || pollDelay.isZero() || maxAttempts < 1) {
            throw new IllegalArgumentException("Email configuration contains an invalid value");
        }
    }

    byte[] decodedOutboxEncryptionKey() {
        try {
            byte[] key = Base64.getDecoder().decode(outboxEncryptionKey);
            if (key.length != 32) {
                throw new IllegalArgumentException("The email outbox encryption key must contain exactly 32 bytes");
            }
            return key;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "The email outbox encryption key must be a Base64-encoded 32-byte AES-256 key", exception);
        }
    }
}
