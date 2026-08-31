package com.project.backend.application.dto;

/**
 * The transactional messages the application can ask infrastructure to send.
 *
 * <p>The enum stays free of delivery detail — subject, template, and outbox
 * event type are chosen by the adapter — so adding a message type never drags
 * SMTP or persistence vocabulary into the application layer.
 */
public enum OutboundEmailKind {
    /** Invites the intended payer to review and accept a loan proposal. */
    LOAN_INVITATION(true),

    /** Confirms ownership of the address used to register an account. */
    ACCOUNT_EMAIL_VERIFICATION(true),

    /**
     * Answers a registration attempt for an address that already has an active
     * account. It carries no token: it exists so the endpoint can respond
     * identically whether or not the address is registered, while still telling
     * the real owner that someone tried.
     */
    EXISTING_ACCOUNT_NOTICE(false);

    private final boolean carriesToken;

    OutboundEmailKind(boolean carriesToken) {
        this.carriesToken = carriesToken;
    }

    public boolean carriesToken() {
        return carriesToken;
    }
}
