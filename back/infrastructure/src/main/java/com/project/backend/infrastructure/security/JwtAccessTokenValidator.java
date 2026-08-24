package com.project.backend.infrastructure.security;

import com.project.backend.application.port.out.ClockPort;
import com.project.backend.application.port.out.AccessTokenValidationPort;
import com.project.backend.application.security.AuthenticatedAccessToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Verifies JWT integrity and current server-side session/account authorization state. */
@Component
public final class JwtAccessTokenValidator implements AccessTokenValidationPort {
    private final JdbcTemplate jdbcTemplate;
    private final ClockPort clock;
    private final JwtAccessTokenCodec codec;

    public JwtAccessTokenValidator(JdbcTemplate jdbcTemplate, ClockPort clock, JwtAuthenticationProperties properties) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "The JDBC template is required");
        this.clock = Objects.requireNonNull(clock, "The clock is required");
        this.codec = new JwtAccessTokenCodec(properties);
    }

    @Override
    public Optional<AuthenticatedAccessToken> validate(String rawToken) {
        try {
            Instant now = clock.now();
            AuthenticatedAccessToken token = codec.decodeAndVerify(rawToken, now);
            Boolean active = jdbcTemplate.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1
                        FROM loans.user_sessions session
                        JOIN loans.user_accounts account ON account.user_account_id = session.user_account_id
                        WHERE session.session_id = ?
                          AND session.user_account_id = ?
                          AND session.revoked_at IS NULL
                          AND session.expires_at > ?
                          AND account.status = 'ACTIVE'
                          AND account.authorization_version = ?
                    )
                    """, Boolean.class, token.sessionId(), token.accountId().value(), now, token.authorizationVersion());
            return Boolean.TRUE.equals(active) ? Optional.of(token) : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
