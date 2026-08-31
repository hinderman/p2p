package com.project.backend.application.port.out;

import com.project.backend.application.dto.AccountVerificationPurpose;
import com.project.backend.application.dto.ClaimedAccountVerification;
import com.project.backend.application.dto.IssuedAccountVerification;
import com.project.backend.domain.valueobject.UserAccountId;

import java.time.Instant;
import java.util.Optional;

/**
 * Issues and redeems the single-use tokens that prove control of an address.
 *
 * <p>The raw token never reaches storage: the adapter keeps only its digest, the
 * same way loan invitations work. Issuing supersedes any token still pending for
 * the same account and purpose, so a resend invalidates the previous link.
 */
public interface AccountVerificationPort {

    IssuedAccountVerification issue(
            UserAccountId accountId, AccountVerificationPurpose purpose, Instant issuedAt);

    /**
     * Consumes the token atomically. A token that is unknown, already used,
     * revoked, or expired yields an empty result — the caller must not be able
     * to tell which.
     */
    Optional<ClaimedAccountVerification> claim(
            String rawToken, AccountVerificationPurpose purpose, Instant claimedAt);

    /** Records that the account's primary address is now proven to be reachable. */
    void markPrimaryEmailVerified(UserAccountId accountId, Instant verifiedAt);
}
