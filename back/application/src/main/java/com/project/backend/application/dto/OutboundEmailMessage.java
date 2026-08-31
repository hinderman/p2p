package com.project.backend.application.dto;

import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.LoanInvitationId;
import com.project.backend.domain.valueobject.UserAccountId;

import java.util.Objects;
import java.util.UUID;

/**
 * Sensitive, short-lived input for the transactional-email outbox.
 *
 * <p>The instance holds the recipient and, for the kinds that need one, the raw
 * single-use token. It is encrypted before it reaches the outbox table and is
 * never logged.
 */
public record OutboundEmailMessage(
        OutboundEmailKind kind,
        UUID referenceId,
        EmailAddress recipientEmail,
        String rawToken) {

    public OutboundEmailMessage {
        Objects.requireNonNull(kind, "The email kind is required");
        Objects.requireNonNull(referenceId, "The email reference identifier is required");
        Objects.requireNonNull(recipientEmail, "The email recipient is required");
        if (kind.carriesToken() && (rawToken == null || rawToken.isBlank())) {
            throw new IllegalArgumentException("A %s email requires a token".formatted(kind));
        }
        if (!kind.carriesToken() && rawToken != null) {
            throw new IllegalArgumentException("A %s email must not carry a token".formatted(kind));
        }
    }

    public static OutboundEmailMessage loanInvitation(
            LoanInvitationId invitationId, EmailAddress recipient, String rawToken) {
        Objects.requireNonNull(invitationId, "The invitation identifier is required");
        return new OutboundEmailMessage(OutboundEmailKind.LOAN_INVITATION, invitationId.value(), recipient, rawToken);
    }

    public static OutboundEmailMessage accountEmailVerification(
            UUID verificationId, EmailAddress recipient, String rawToken) {
        return new OutboundEmailMessage(OutboundEmailKind.ACCOUNT_EMAIL_VERIFICATION, verificationId, recipient, rawToken);
    }

    public static OutboundEmailMessage existingAccountNotice(UserAccountId accountId, EmailAddress recipient) {
        Objects.requireNonNull(accountId, "The account identifier is required");
        return new OutboundEmailMessage(OutboundEmailKind.EXISTING_ACCOUNT_NOTICE, accountId.value(), recipient, null);
    }
}
