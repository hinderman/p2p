package com.project.backend.application.port.out;

import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.LoanTermId;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.application.dto.ClaimedLoanInvitation;
import com.project.backend.domain.valueobject.UserAccountId;
import com.project.backend.application.dto.ScheduledLoanInvitation;

import java.time.Instant;
import java.util.Optional;

public interface LoanInvitationPort {
    /**
     * Persists the one-time invitation. Its raw token is returned only to create
     * an encrypted outbox delivery request in the surrounding transaction.
     */
    ScheduledLoanInvitation scheduleInvitation(
            LoanId loanId, LoanTermId loanTermId, EmailAddress recipient, Instant createdAt);

    /** Claims a pending, unexpired invitation. The surrounding unit of work must be transactional. */
    Optional<ClaimedLoanInvitation> claimForPayerOnboarding(String rawToken, Instant acceptedAt);

    void recordPayerAcceptance(
            ClaimedLoanInvitation invitation,
            UserAccountId accountId,
            String sourceIp,
            String userAgent,
            Instant acceptedAt);
}
