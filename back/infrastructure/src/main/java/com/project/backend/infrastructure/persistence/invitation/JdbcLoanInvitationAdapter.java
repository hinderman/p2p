package com.project.backend.infrastructure.persistence.invitation;

import com.project.backend.application.port.out.LoanInvitationPort;
import com.project.backend.application.dto.ClaimedLoanInvitation;
import com.project.backend.application.dto.ScheduledLoanInvitation;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.LoanInvitationId;
import com.project.backend.domain.valueobject.LoanTermId;
import com.project.backend.domain.valueobject.UserAccountId;
import com.project.backend.infrastructure.security.SingleUseToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.Optional;

import static com.project.backend.infrastructure.persistence.JdbcTime.timestamp;
import static com.project.backend.infrastructure.persistence.JdbcTime.instant;

/** Persists a single-use invitation token; only its SHA-256 digest is retained. */
@Component
public final class JdbcLoanInvitationAdapter implements LoanInvitationPort {
    private final JdbcTemplate jdbcTemplate;

    public JdbcLoanInvitationAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ScheduledLoanInvitation scheduleInvitation(
            LoanId loanId, LoanTermId loanTermId, EmailAddress recipient, Instant createdAt) {
        Instant now = java.util.Objects.requireNonNull(createdAt, "The invitation time is required");
        UUID invitationId = UUID.randomUUID();
        String rawToken = SingleUseToken.generate();
        jdbcTemplate.update("UPDATE loans.loan_invitations SET status = 'REVOKED', revoked_at = ? WHERE loan_term_id = ? AND status = 'PENDING'",
                timestamp(now), loanTermId.value());
        int inserted = jdbcTemplate.update("""
                INSERT INTO loans.loan_invitations
                (loan_invitation_id, loan_term_id, normalized_email, token_hash, status, expires_at, created_at,
                 created_by_user_account_id)
                SELECT ?, ?, ?, ?, 'PENDING', ?, ?, account.user_account_id
                FROM loans.loans loan JOIN loans.user_accounts account ON account.person_id = loan.lender_person_id
                WHERE loan.loan_id = ?
                """, invitationId, loanTermId.value(), recipient.value(), SingleUseToken.digest(rawToken),
                timestamp(now.plus(7, ChronoUnit.DAYS)), timestamp(now), loanId.value());
        if (inserted != 1) {
            throw new IllegalStateException("The invitation could not be created for the loan");
        }
        return new ScheduledLoanInvitation(new LoanInvitationId(invitationId), recipient, rawToken);
    }

    @Override
    public Optional<ClaimedLoanInvitation> claimForPayerOnboarding(String rawToken, Instant acceptedAt) {
        var invitations = jdbcTemplate.query("""
                SELECT invitation.loan_invitation_id, invitation.loan_term_id, invitation.normalized_email,
                       invitation.status, invitation.expires_at, term.loan_id
                FROM loans.loan_invitations invitation
                JOIN loans.loan_terms term ON term.loan_term_id = invitation.loan_term_id
                WHERE invitation.token_hash = ?
                FOR UPDATE OF invitation
                """, (resultSet, rowNumber) -> new InvitationRecord(
                resultSet.getObject("loan_invitation_id", UUID.class),
                resultSet.getObject("loan_term_id", UUID.class),
                resultSet.getObject("loan_id", UUID.class),
                resultSet.getString("normalized_email"),
                resultSet.getString("status"),
                instant(resultSet, "expires_at")), SingleUseToken.digest(rawToken));
        if (invitations.isEmpty()) {
            return Optional.empty();
        }
        InvitationRecord invitation = invitations.getFirst();
        if (!"PENDING".equals(invitation.status())) {
            return Optional.empty();
        }
        if (!invitation.expiresAt().isAfter(acceptedAt)) {
            jdbcTemplate.update("""
                    UPDATE loans.loan_invitations SET status = 'EXPIRED'
                    WHERE loan_invitation_id = ? AND status = 'PENDING'
                    """, invitation.id());
            return Optional.empty();
        }
        int updated = jdbcTemplate.update("""
                UPDATE loans.loan_invitations SET status = 'ACCEPTED', accepted_at = ?
                WHERE loan_invitation_id = ? AND status = 'PENDING'
                """, timestamp(acceptedAt), invitation.id());
        if (updated != 1) {
            return Optional.empty();
        }
        return Optional.of(new ClaimedLoanInvitation(new LoanInvitationId(invitation.id()), new LoanId(invitation.loanId()),
                new LoanTermId(invitation.loanTermId()), new EmailAddress(invitation.recipientEmail())));
    }

    @Override
    public void recordPayerAcceptance(
            ClaimedLoanInvitation invitation,
            UserAccountId accountId,
            String sourceIp,
            String userAgent,
            Instant acceptedAt) {
        jdbcTemplate.update("""
                INSERT INTO loans.loan_term_acceptances
                (loan_term_acceptance_id, loan_term_id, user_account_id, accepting_role, loan_invitation_id,
                 source_ip, user_agent, accepted_at)
                VALUES (?, ?, ?, 'PAYER', ?, CAST(? AS inet), ?, ?)
                """, UUID.randomUUID(), invitation.loanTermId().value(), accountId.value(), invitation.invitationId().value(),
                sourceIp, userAgent, timestamp(acceptedAt));
    }

    private record InvitationRecord(
            UUID id, UUID loanTermId, UUID loanId, String recipientEmail, String status, Instant expiresAt) { }
}
