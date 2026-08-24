package com.project.backend.application.dto;

import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.LoanInvitationId;

import java.util.Objects;

/** Sensitive, short-lived input for the invitation-email outbox. */
public record InvitationEmailMessage(
        LoanInvitationId invitationId,
        EmailAddress recipientEmail,
        String rawToken) {

    public InvitationEmailMessage {
        Objects.requireNonNull(invitationId, "The invitation identifier is required");
        Objects.requireNonNull(recipientEmail, "The invitation recipient is required");
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("The invitation token is required");
        }
    }
}
