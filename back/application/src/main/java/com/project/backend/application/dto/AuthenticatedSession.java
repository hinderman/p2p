package com.project.backend.application.dto;

import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.valueobject.UserAccountId;
import com.project.backend.domain.valueobject.PersonId;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Authentication result. Infrastructure decides how tokens are transported. */
public record AuthenticatedSession(
        UserAccountId userAccountId,
        PersonId personId,
        Set<UserRole> roles,
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt) {
    public AuthenticatedSession {
        Objects.requireNonNull(userAccountId, "La account es obligatoria");
        Objects.requireNonNull(personId, "La person es obligatoria");
        roles = Set.copyOf(Objects.requireNonNull(roles, "Los roles son obligatorios"));
        if (roles.isEmpty()) throw new IllegalArgumentException("A session requires at least one role");
        Objects.requireNonNull(accessToken, "El access token es obligatorio");
        Objects.requireNonNull(refreshToken, "El refresh token es obligatorio");
        Objects.requireNonNull(accessTokenExpiresAt, "An expiration time is required");
    }
}
