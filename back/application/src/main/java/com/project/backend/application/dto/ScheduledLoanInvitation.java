package com.project.backend.application.dto;

import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.LoanInvitationId;

import java.util.Objects;

/**
 * Ephemeral result of creating an invitation. The raw token is intentionally
 * available only long enough to create an encrypted email-outbox message.
 */
public record ScheduledLoanInvitation(
        LoanInvitationId invitationId,
        EmailAddress recipientEmail,
        String rawToken) {

    public ScheduledLoanInvitation {
        Objects.requireNonNull(invitationId, "The invitation identifier is required");
        Objects.requireNonNull(recipientEmail, "The invitation recipient is required");
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("The invitation token is required");
        }
    }
}
