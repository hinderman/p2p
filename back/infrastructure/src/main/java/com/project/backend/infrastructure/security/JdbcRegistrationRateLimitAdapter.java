package com.project.backend.infrastructure.security;

import com.project.backend.application.exception.RateLimitExceededException;
import com.project.backend.application.port.out.RegistrationRateLimitPort;
import com.project.backend.domain.valueobject.EmailAddress;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static com.project.backend.infrastructure.persistence.JdbcTime.timestamp;

/** PostgreSQL-backed rolling-window throttle shared by every application instance. */
@Component
public final class JdbcRegistrationRateLimitAdapter implements RegistrationRateLimitPort {
    private final JdbcTemplate jdbcTemplate;
    private final RegistrationRateLimitProperties properties;

    public JdbcRegistrationRateLimitAdapter(
            JdbcTemplate jdbcTemplate, RegistrationRateLimitProperties properties) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "The JDBC template is required");
        this.properties = Objects.requireNonNull(properties, "The rate-limit properties are required");
    }

    @Override
    public void checkAllowed(EmailAddress email, String sourceIp, Instant occurredAt) {
        Instant from = occurredAt.minus(properties.window());
        if (attemptsForEmail(email, from) >= properties.maxAttemptsPerEmail()
                || (sourceIp != null && attemptsForIp(sourceIp, from) >= properties.maxAttemptsPerIp())) {
            throw new RateLimitExceededException();
        }
    }

    @Override
    public void recordAttempt(EmailAddress email, String sourceIp, Instant occurredAt) {
        jdbcTemplate.update("""
                INSERT INTO loans.registration_attempts
                (registration_attempt_id, normalized_email, source_ip, occurred_at)
                VALUES (?, ?, CAST(? AS inet), ?)
                """, UUID.randomUUID(), email.value(), sourceIp, timestamp(occurredAt));
    }

    private long attemptsForEmail(EmailAddress email, Instant from) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM loans.registration_attempts
                WHERE normalized_email = ? AND occurred_at >= ?
                """, Long.class, email.value(), timestamp(from));
        return count == null ? 0 : count;
    }

    private long attemptsForIp(String sourceIp, Instant from) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM loans.registration_attempts
                WHERE source_ip = CAST(? AS inet) AND occurred_at >= ?
                """, Long.class, sourceIp, timestamp(from));
        return count == null ? 0 : count;
    }
}
