package com.project.backend.infrastructure.security;

import com.project.backend.application.exception.RateLimitExceededException;
import com.project.backend.application.port.out.SignInRateLimitPort;
import com.project.backend.domain.valueobject.EmailAddress;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static com.project.backend.infrastructure.persistence.JdbcTime.timestamp;

/** PostgreSQL-backed rolling-window throttle that works consistently across application instances. */
@Component
public final class JdbcSignInRateLimitAdapter implements SignInRateLimitPort {
    private final JdbcTemplate jdbcTemplate;
    private final AuthenticationRateLimitProperties properties;

    public JdbcSignInRateLimitAdapter(JdbcTemplate jdbcTemplate, AuthenticationRateLimitProperties properties) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "The JDBC template is required");
        this.properties = Objects.requireNonNull(properties, "The rate-limit properties are required");
    }

    @Override
    public void checkAllowed(EmailAddress email, String sourceIp, Instant occurredAt) {
        Instant from = occurredAt.minus(properties.window());
        if (failedAttemptsForEmail(email, from) >= properties.maxFailuresPerEmail()
                || (sourceIp != null && failedAttemptsForIp(sourceIp, from) >= properties.maxFailuresPerIp())) {
            throw new RateLimitExceededException();
        }
    }

    @Override
    public void recordAttempt(EmailAddress email, String sourceIp, boolean succeeded, Instant occurredAt) {
        jdbcTemplate.update("""
                INSERT INTO loans.sign_in_attempts
                (sign_in_attempt_id, normalized_email, source_ip, succeeded, occurred_at)
                VALUES (?, ?, CAST(? AS inet), ?, ?)
                """, UUID.randomUUID(), email.value(), sourceIp, succeeded, timestamp(occurredAt));
    }

    private long failedAttemptsForEmail(EmailAddress email, Instant from) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM loans.sign_in_attempts
                WHERE normalized_email = ? AND succeeded = false AND occurred_at >= ?
                """, Long.class, email.value(), timestamp(from));
        return count == null ? 0 : count;
    }

    private long failedAttemptsForIp(String sourceIp, Instant from) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM loans.sign_in_attempts
                WHERE source_ip = CAST(? AS inet) AND succeeded = false AND occurred_at >= ?
                """, Long.class, sourceIp, timestamp(from));
        return count == null ? 0 : count;
    }
}
