package com.project.backend.application.port.out;

import com.project.backend.domain.valueobject.EmailAddress;

import java.time.Instant;

/** Persistent brute-force protection policy for sign-in attempts. */
public interface SignInRateLimitPort {

    void checkAllowed(EmailAddress email, String sourceIp, Instant occurredAt);

    void recordAttempt(EmailAddress email, String sourceIp, boolean succeeded, Instant occurredAt);
}
