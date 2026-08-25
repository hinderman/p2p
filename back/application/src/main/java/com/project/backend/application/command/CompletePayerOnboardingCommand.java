package com.project.backend.application.command;

import com.project.backend.application.dto.Command;
import com.project.backend.application.dto.AuthenticatedSession;

import java.util.Arrays;
import java.util.Objects;

/** One-time invitation-token redemption, payer activation, and loan acceptance intent. */
public record CompletePayerOnboardingCommand(
        String invitationToken,
        char[] password,
        String sourceIp,
        String userAgent) implements Command<AuthenticatedSession> {
    public CompletePayerOnboardingCommand {
        invitationToken = Objects.requireNonNull(invitationToken, "The invitation token is required").strip();
        if (invitationToken.isEmpty() || invitationToken.length() > 512) {
            throw new IllegalArgumentException("The invitation token is invalid");
        }
        password = Objects.requireNonNull(password, "The password is required").clone();
        if (password.length < 12 || password.length > 128) {
            Arrays.fill(password, '\0');
            throw new IllegalArgumentException("The password must contain between 12 and 128 characters");
        }
        sourceIp = sourceIp == null ? null : sourceIp.strip();
        userAgent = userAgent == null ? null : userAgent.strip();
        if (userAgent != null && userAgent.length() > 1_000) {
            throw new IllegalArgumentException("The user agent is too long");
        }
    }

    @Override
    public char[] password() {
        return password.clone();
    }
}
