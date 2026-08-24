package com.project.backend.infrastructure.security;

import com.project.backend.application.dto.AuthenticatedSession;
import com.project.backend.application.port.out.AuthenticationTokenIssuerPort;
import com.project.backend.application.port.out.AuthenticationSessionPort;
import com.project.backend.domain.identity.UserAccount;
import com.project.backend.domain.identity.UserAccountStatus;
import com.project.backend.domain.repository.UserAccountRepository;
import com.project.backend.domain.valueobject.UserAccountId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Issues short-lived signed access tokens and persists rotating refresh-token state. */
@Component
public final class JwtAuthenticationTokenIssuer implements AuthenticationTokenIssuerPort, AuthenticationSessionPort {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final JdbcTemplate jdbcTemplate;
    private final JwtAuthenticationProperties properties;
    private final UserAccountRepository accounts;
    private final JwtAccessTokenCodec codec;

    public JwtAuthenticationTokenIssuer(
            JdbcTemplate jdbcTemplate, JwtAuthenticationProperties properties, UserAccountRepository accounts) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.accounts = accounts;
        this.codec = new JwtAccessTokenCodec(properties);
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

        return new AuthenticatedSession(account.id(), account.personId(), account.roles(),
                codec.issue(account, sessionId, issuedAt, accessTokenExpiresAt), refreshToken, accessTokenExpiresAt);
    }

    @Override
    @Transactional
    public java.util.Optional<AuthenticatedSession> rotateRefreshToken(String refreshToken, Instant occurredAt) {
        String hash = sha256(refreshToken);
        var records = jdbcTemplate.query("""
                SELECT token.refresh_token_id, token.session_id, token.expires_at AS token_expires_at,
                       token.consumed_at, token.revoked_at AS token_revoked_at,
                       session.user_account_id, session.expires_at AS session_expires_at, session.revoked_at AS session_revoked_at
                FROM loans.refresh_tokens token
                JOIN loans.user_sessions session ON session.session_id = token.session_id
                WHERE token.token_hash = ?
                FOR UPDATE OF token, session
                """, (resultSet, rowNumber) -> new RefreshTokenRecord(
                resultSet.getObject("refresh_token_id", UUID.class),
                resultSet.getObject("session_id", UUID.class),
                resultSet.getObject("user_account_id", UUID.class),
                resultSet.getObject("token_expires_at", Instant.class),
                resultSet.getObject("session_expires_at", Instant.class),
                resultSet.getObject("consumed_at", Instant.class),
                resultSet.getObject("token_revoked_at", Instant.class),
                resultSet.getObject("session_revoked_at", Instant.class)), hash);
        if (records.isEmpty()) {
            return java.util.Optional.empty();
        }
        RefreshTokenRecord record = records.getFirst();
        if (record.consumedAt() != null || record.tokenRevokedAt() != null || record.sessionRevokedAt() != null
                || !record.tokenExpiresAt().isAfter(occurredAt) || !record.sessionExpiresAt().isAfter(occurredAt)) {
            if (record.consumedAt() != null) {
                revokeSession(record.sessionId(), occurredAt, "REFRESH_TOKEN_REUSE");
            }
            return java.util.Optional.empty();
        }
        UserAccount account = accounts.findById(new UserAccountId(record.accountId())).orElse(null);
        if (account == null || account.status() != UserAccountStatus.ACTIVE) {
            revokeSession(record.sessionId(), occurredAt, "ACCOUNT_NOT_ACTIVE");
            return java.util.Optional.empty();
        }
        Instant refreshExpiresAt = min(record.sessionExpiresAt(), occurredAt.plus(properties.refreshTokenTtl()));
        Instant accessExpiresAt = min(record.sessionExpiresAt(), occurredAt.plus(properties.accessTokenTtl()));
        if (!refreshExpiresAt.isAfter(occurredAt) || !accessExpiresAt.isAfter(occurredAt)) {
            return java.util.Optional.empty();
        }
        UUID replacementId = UUID.randomUUID();
        String replacementToken = randomToken();
        jdbcTemplate.update("""
                INSERT INTO loans.refresh_tokens (refresh_token_id, session_id, token_hash, issued_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """, replacementId, record.sessionId(), sha256(replacementToken), occurredAt, refreshExpiresAt);
        jdbcTemplate.update("UPDATE loans.refresh_tokens SET consumed_at = ?, replaced_by_id = ? WHERE refresh_token_id = ?",
                occurredAt, replacementId, record.refreshTokenId());
        return java.util.Optional.of(new AuthenticatedSession(account.id(), account.personId(), account.roles(),
                codec.issue(account, record.sessionId(), occurredAt, accessExpiresAt), replacementToken, accessExpiresAt));
    }

    @Override
    @Transactional
    public void revokeAllActiveSessions(UserAccountId accountId, Instant occurredAt) {
        jdbcTemplate.update("""
                UPDATE loans.refresh_tokens token SET revoked_at = ?
                FROM loans.user_sessions session
                WHERE token.session_id = session.session_id
                  AND session.user_account_id = ?
                  AND session.revoked_at IS NULL
                  AND session.expires_at > ?
                  AND token.revoked_at IS NULL
                """, occurredAt, accountId.value(), occurredAt);
        jdbcTemplate.update("""
                UPDATE loans.user_sessions SET revoked_at = ?, revocation_reason = 'USER_SIGN_OUT'
                WHERE user_account_id = ? AND revoked_at IS NULL AND expires_at > ?
                """, occurredAt, accountId.value(), occurredAt);
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

    private void revokeSession(UUID sessionId, Instant occurredAt, String reason) {
        jdbcTemplate.update("""
                UPDATE loans.refresh_tokens SET revoked_at = ?
                WHERE session_id = ? AND revoked_at IS NULL
                """, occurredAt, sessionId);
        jdbcTemplate.update("""
                UPDATE loans.user_sessions SET revoked_at = ?, revocation_reason = ?
                WHERE session_id = ? AND revoked_at IS NULL
                """, occurredAt, reason, sessionId);
    }

    private static Instant min(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private record RefreshTokenRecord(
            UUID refreshTokenId, UUID sessionId, UUID accountId, Instant tokenExpiresAt, Instant sessionExpiresAt,
            Instant consumedAt, Instant tokenRevokedAt, Instant sessionRevokedAt) { }
}
