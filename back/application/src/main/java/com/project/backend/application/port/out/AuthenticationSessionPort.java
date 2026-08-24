package com.project.backend.application.port.out;

import com.project.backend.application.dto.AuthenticatedSession;
import com.project.backend.domain.valueobject.UserAccountId;

import java.time.Instant;
import java.util.Optional;

/** Manages refresh-token rotation and session revocation outside the application core. */
public interface AuthenticationSessionPort {

    Optional<AuthenticatedSession> rotateRefreshToken(String refreshToken, Instant occurredAt);

    void revokeAllActiveSessions(UserAccountId accountId, Instant occurredAt);
}
