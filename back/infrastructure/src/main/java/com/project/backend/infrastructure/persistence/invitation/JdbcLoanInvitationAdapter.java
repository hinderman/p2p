package com.project.backend.infrastructure.persistence.invitation;

import com.project.backend.application.port.out.LoanInvitationPort;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.LoanTermId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Persists a single-use invitation token; only its SHA-256 digest is retained. */
@Component
public final class JdbcLoanInvitationAdapter implements LoanInvitationPort {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final JdbcTemplate jdbcTemplate;

    public JdbcLoanInvitationAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void scheduleInvitation(LoanId loanId, LoanTermId loanTermId, EmailAddress recipient) {
        Instant now = Instant.now();
        jdbcTemplate.update("UPDATE loans.loan_invitations SET status = 'REVOKED', revoked_at = ? WHERE loan_term_id = ? AND status = 'PENDING'",
                now, loanTermId.value());
        jdbcTemplate.update("""
                INSERT INTO loans.loan_invitations
                (loan_invitation_id, loan_term_id, normalized_email, token_hash, status, expires_at, created_at,
                 created_by_user_account_id)
                SELECT ?, ?, ?, ?, 'PENDING', ?, ?, account.user_account_id
                FROM loans.loans loan JOIN loans.user_accounts account ON account.person_id = loan.lender_person_id
                WHERE loan.loan_id = ?
                """, UUID.randomUUID(), loanTermId.value(), recipient.value(), sha256(randomToken()),
                now.plus(7, ChronoUnit.DAYS), now, loanId.value());
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
