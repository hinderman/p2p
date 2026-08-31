package com.project.backend.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * A freshly issued single-use verification token.
 *
 * <p>{@code rawToken} exists only for the duration of the transaction that
 * enqueues the email; persistence retains its digest alone.
 */
public record IssuedAccountVerification(UUID verificationId, String rawToken) {
    public IssuedAccountVerification {
        Objects.requireNonNull(verificationId, "The verification identifier is required");
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("The verification token is required");
        }
    }
}
