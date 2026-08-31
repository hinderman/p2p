package com.project.backend.application.command;

import com.project.backend.application.dto.AuthenticatedSession;
import com.project.backend.application.dto.Command;

import java.util.Objects;

/** Redeems the single-use link that proves the registrant controls the address. */
public record VerifyAccountEmailCommand(String verificationToken) implements Command<AuthenticatedSession> {
    public VerifyAccountEmailCommand {
        verificationToken = Objects.requireNonNull(verificationToken, "The verification token is required").strip();
        if (verificationToken.isEmpty() || verificationToken.length() > 512) {
            throw new IllegalArgumentException("The verification token is invalid");
        }
    }
}
