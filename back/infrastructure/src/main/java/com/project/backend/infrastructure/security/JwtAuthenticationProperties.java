package com.project.backend.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

@ConfigurationProperties("security.authentication.jwt")
public record JwtAuthenticationProperties(
        String hmacSecret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl) {

    public JwtAuthenticationProperties {
        Objects.requireNonNull(hmacSecret, "JWT HMAC secret is required");
        Objects.requireNonNull(issuer, "JWT issuer is required");
        Objects.requireNonNull(accessTokenTtl, "Access-token TTL is required");
        Objects.requireNonNull(refreshTokenTtl, "Refresh-token TTL is required");
        if (issuer.isBlank() || accessTokenTtl.isNegative() || accessTokenTtl.isZero()
                || refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
            throw new IllegalArgumentException("JWT configuration contains an invalid value");
        }
    }

    public byte[] decodedHmacSecret() {
        try {
            byte[] secret = Base64.getDecoder().decode(hmacSecret);
            if (secret.length < 64) {
                throw new IllegalArgumentException("JWT HMAC secret must contain at least 64 bytes");
            }
            return secret;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT HMAC secret must be Base64 encoded and at least 64 bytes long", exception);
        }
    }
}
