package com.project.backend.application.port.out;

import com.project.backend.domain.valueobject.EmailAddress;

import java.time.Instant;

/**
 * Throttles account-creation and verification-resend traffic.
 *
 * <p>Unlike sign-in, every attempt counts, not only the failed ones: these
 * endpoints answer identically whether or not the address exists, so there is no
 * "failure" to count, and the abuse to prevent is volume — mailbox flooding and
 * enumeration by timing.
 */
public interface RegistrationRateLimitPort {

    /** @throws com.project.backend.application.exception.RateLimitExceededException when the window is exhausted. */
    void checkAllowed(EmailAddress email, String sourceIp, Instant occurredAt);

    void recordAttempt(EmailAddress email, String sourceIp, Instant occurredAt);
}
