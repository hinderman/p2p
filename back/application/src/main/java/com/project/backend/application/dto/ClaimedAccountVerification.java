package com.project.backend.application.dto;

import com.project.backend.domain.valueobject.UserAccountId;

import java.util.Objects;
import java.util.UUID;

/** A verification token that this transaction consumed; it cannot be redeemed again. */
public record ClaimedAccountVerification(
        UUID verificationId,
        UserAccountId userAccountId,
        AccountVerificationPurpose purpose) {

    public ClaimedAccountVerification {
        Objects.requireNonNull(verificationId, "The verification identifier is required");
        Objects.requireNonNull(userAccountId, "The verified account is required");
        Objects.requireNonNull(purpose, "The verification purpose is required");
    }
}
