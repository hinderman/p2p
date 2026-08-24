package com.project.backend.api.rest;

import com.project.backend.api.exception.AuthenticationRequiredException;
import com.project.backend.domain.valueobject.UserAccountId;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;

/**
 * Boundary between HTTP authentication and application commands. The JWT filter
 * introduced in the security phase will set the principal name to the account UUID.
 */
@Component
public final class CurrentAccountResolver {

    public UserAccountId requireAccountId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new AuthenticationRequiredException();
        }
        try {
            return new UserAccountId(UUID.fromString(principal.getName()));
        } catch (IllegalArgumentException exception) {
            throw new AuthenticationRequiredException();
        }
    }
}
