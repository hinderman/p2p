package com.project.backend.api.dto.response;

import com.project.backend.domain.identity.UserRole;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AuthenticatedSessionResponse(
        UUID userAccountId,
        UUID personId,
        Set<UserRole> roles,
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt) {
}
