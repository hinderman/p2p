package com.project.backend.infrastructure.security;

import com.project.backend.application.dto.AuthenticatedSession;
import com.project.backend.application.port.out.AuthenticationTokenIssuerPort;
import com.project.backend.domain.identity.UserAccount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Issues short-lived signed access tokens and persists rotating refresh-token state. */
@Component
public final class JwtAuthenticationTokenIssuer implements AuthenticationTokenIssuerPort {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final JdbcTemplate jdbcTemplate;
    private final JwtAuthenticationProperties properties;
    private final byte[] signingKey;

    public JwtAuthenticationTokenIssuer(JdbcTemplate jdbcTemplate, JwtAuthenticationProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.signingKey = properties.decodedHmacSecret();
    }

    @Override
    @Transactional
    public AuthenticatedSession issue(UserAccount account, Instant issuedAt) {
        Instant accessTokenExpiresAt = issuedAt.plus(properties.accessTokenTtl());
        Instant refreshTokenExpiresAt = issuedAt.plus(properties.refreshTokenTtl());
        UUID sessionId = UUID.randomUUID();
        UUID refreshTokenId = UUID.randomUUID();
        String refreshToken = randomToken();

        jdbcTemplate.update("""
                INSERT INTO loans.user_sessions (session_id, user_account_id, created_at, expires_at)
                VALUES (?, ?, ?, ?)
                """, sessionId, account.id().value(), issuedAt, refreshTokenExpiresAt);
        jdbcTemplate.update("""
                INSERT INTO loans.refresh_tokens (refresh_token_id, session_id, token_hash, issued_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """, refreshTokenId, sessionId, sha256(refreshToken), issuedAt, refreshTokenExpiresAt);
        jdbcTemplate.update("""
                UPDATE loans.user_accounts SET last_sign_in_at = ?, updated_at = ?, version = version + 1
                WHERE user_account_id = ?
                """, issuedAt, issuedAt, account.id().value());

        return new AuthenticatedSession(
                account.id(), account.personId(), account.roles(),
                signedAccessToken(account, sessionId, issuedAt, accessTokenExpiresAt),
                refreshToken, accessTokenExpiresAt);
    }

    private String signedAccessToken(UserAccount account, UUID sessionId, Instant issuedAt, Instant expiresAt) {
        String header = base64Url("{\"alg\":\"HS512\",\"typ\":\"JWT\"}");
        String roles = account.roles().stream().map(role -> "\"" + role.name() + "\"").sorted()
                .reduce((left, right) -> left + "," + right).orElseThrow();
        String payload = "{\"iss\":\"" + escapeJson(properties.issuer()) + "\","
                + "\"sub\":\"" + account.id().value() + "\","
                + "\"person_id\":\"" + account.personId().value() + "\","
                + "\"session_id\":\"" + sessionId + "\","
                + "\"roles\":[" + roles + "],"
                + "\"authorization_version\":" + account.authorizationVersion() + ","
                + "\"iat\":" + issuedAt.getEpochSecond() + ","
                + "\"nbf\":" + issuedAt.getEpochSecond() + ","
                + "\"exp\":" + expiresAt.getEpochSecond() + ","
                + "\"jti\":\"" + UUID.randomUUID() + "\"}";
        String content = header + "." + base64Url(payload);
        return content + "." + URL_ENCODER.encodeToString(hmacSha512(content));
    }

    private byte[] hmacSha512(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA512"));
            return mac.doFinal(content.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign authentication token", exception);
        }
    }

    private static String randomToken() {
        byte[] bytes = new byte[64];
        SECURE_RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String base64Url(String value) {
        return URL_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
