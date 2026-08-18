package com.project.backend.application.port.out;

import com.project.backend.application.dto.AuthenticatedSession;
import com.project.backend.domain.identity.UserAccount;

import java.time.Instant;

public interface AuthenticationTokenIssuerPort {
    AuthenticatedSession issue(UserAccount account, Instant issuedAt);
}
