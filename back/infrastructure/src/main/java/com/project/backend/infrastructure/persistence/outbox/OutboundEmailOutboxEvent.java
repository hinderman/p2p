package com.project.backend.infrastructure.persistence.outbox;

import com.project.backend.application.dto.OutboundEmailKind;

import java.util.Arrays;
import java.util.List;

/**
 * Maps each message kind onto the outbox row that represents it.
 *
 * <p>The names are a persistence contract: existing rows carry them, so a kind's
 * event type must stay stable once it has been written. Keeping the mapping here
 * lets the writer and the publisher agree without either importing the other.
 */
public final class OutboundEmailOutboxEvent {

    private OutboundEmailOutboxEvent() {
    }

    public static String eventType(OutboundEmailKind kind) {
        return switch (kind) {
            case LOAN_INVITATION -> "LoanInvitationEmailRequested";
            case ACCOUNT_EMAIL_VERIFICATION -> "AccountVerificationEmailRequested";
            case EXISTING_ACCOUNT_NOTICE -> "ExistingAccountNoticeEmailRequested";
        };
    }

    public static String aggregateType(OutboundEmailKind kind) {
        return switch (kind) {
            case LOAN_INVITATION -> "LoanInvitation";
            case ACCOUNT_EMAIL_VERIFICATION, EXISTING_ACCOUNT_NOTICE -> "UserAccount";
        };
    }

    /** Every event type the email publisher is responsible for draining. */
    public static List<String> allEventTypes() {
        return Arrays.stream(OutboundEmailKind.values()).map(OutboundEmailOutboxEvent::eventType).toList();
    }
}
