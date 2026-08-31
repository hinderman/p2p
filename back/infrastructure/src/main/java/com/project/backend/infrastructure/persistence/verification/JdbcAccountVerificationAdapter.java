package com.project.backend.infrastructure.persistence.verification;

import com.project.backend.application.dto.AccountVerificationPurpose;
import com.project.backend.application.dto.ClaimedAccountVerification;
import com.project.backend.application.dto.IssuedAccountVerification;
import com.project.backend.application.port.out.AccountVerificationPort;
import com.project.backend.domain.valueobject.UserAccountId;
import com.project.backend.infrastructure.security.SingleUseToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.project.backend.infrastructure.persistence.JdbcTime.instant;
import static com.project.backend.infrastructure.persistence.JdbcTime.timestamp;

/** Persists single-use account tokens; only the SHA-256 digest is retained. */
@Component
public final class JdbcAccountVerificationAdapter implements AccountVerificationPort {
    private static final Duration LIFETIME = Duration.ofHours(24);

    private final JdbcTemplate jdbcTemplate;

    public JdbcAccountVerificationAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public IssuedAccountVerification issue(
            UserAccountId accountId, AccountVerificationPurpose purpose, Instant issuedAt) {
        jdbcTemplate.update("""
                UPDATE loans.account_verifications SET status = 'REVOKED', revoked_at = ?
                WHERE user_account_id = ? AND purpose = ? AND status = 'PENDING'
                """, timestamp(issuedAt), accountId.value(), purpose.name());

        UUID verificationId = UUID.randomUUID();
        String rawToken = SingleUseToken.generate();
        jdbcTemplate.update("""
                INSERT INTO loans.account_verifications
                (account_verification_id, user_account_id, purpose, token_hash, status, expires_at, created_at)
                VALUES (?, ?, ?, ?, 'PENDING', ?, ?)
                """, verificationId, accountId.value(), purpose.name(), SingleUseToken.digest(rawToken),
                timestamp(issuedAt.plus(LIFETIME)), timestamp(issuedAt));
        return new IssuedAccountVerification(verificationId, rawToken);
    }

    @Override
    public Optional<ClaimedAccountVerification> claim(
            String rawToken, AccountVerificationPurpose purpose, Instant claimedAt) {
        List<VerificationRecord> found = jdbcTemplate.query("""
                SELECT account_verification_id, user_account_id, purpose, status, expires_at
                FROM loans.account_verifications
                WHERE token_hash = ?
                FOR UPDATE
                """, (resultSet, rowNumber) -> new VerificationRecord(
                resultSet.getObject("account_verification_id", UUID.class),
                resultSet.getObject("user_account_id", UUID.class),
                resultSet.getString("purpose"),
                resultSet.getString("status"),
                instant(resultSet, "expires_at")), SingleUseToken.digest(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        VerificationRecord verification = found.getFirst();
        if (!purpose.name().equals(verification.purpose()) || !"PENDING".equals(verification.status())) {
            return Optional.empty();
        }
        if (!verification.expiresAt().isAfter(claimedAt)) {
            jdbcTemplate.update("""
                    UPDATE loans.account_verifications SET status = 'EXPIRED'
                    WHERE account_verification_id = ? AND status = 'PENDING'
                    """, verification.id());
            return Optional.empty();
        }

        int consumed = jdbcTemplate.update("""
                UPDATE loans.account_verifications SET status = 'CONFIRMED', confirmed_at = ?
                WHERE account_verification_id = ? AND status = 'PENDING'
                """, timestamp(claimedAt), verification.id());
        if (consumed != 1) {
            return Optional.empty();
        }
        return Optional.of(new ClaimedAccountVerification(
                verification.id(), new UserAccountId(verification.accountId()), purpose));
    }

    @Override
    public void markPrimaryEmailVerified(UserAccountId accountId, Instant verifiedAt) {
        jdbcTemplate.update("""
                UPDATE loans.person_emails SET verified_at = ?
                WHERE is_primary AND verified_at IS NULL AND person_id =
                    (SELECT person_id FROM loans.user_accounts WHERE user_account_id = ?)
                """, timestamp(verifiedAt), accountId.value());
    }

    private record VerificationRecord(
            UUID id, UUID accountId, String purpose, String status, Instant expiresAt) { }
}
