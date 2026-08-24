package com.project.backend.application.port.out;

import com.project.backend.application.security.AuthenticatedAccessToken;

import java.util.Optional;

/** Validates a presented bearer token and current server-side authorization state. */
public interface AccessTokenValidationPort {

    Optional<AuthenticatedAccessToken> validate(String rawToken);
}
