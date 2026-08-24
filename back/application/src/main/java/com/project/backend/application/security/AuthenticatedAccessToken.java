package com.project.backend.application.security;

import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.UserAccountId;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Verified access-token identity supplied by the security infrastructure adapter. */
public record AuthenticatedAccessToken(
        UserAccountId accountId,
        PersonId personId,
        UUID sessionId,
        Set<UserRole> roles,
        long authorizationVersion,
        Instant issuedAt,
        Instant expiresAt) {
}
