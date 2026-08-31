package com.project.backend.application.command;

import com.project.backend.application.dto.Command;
import com.project.backend.domain.valueobject.EmailAddress;

import java.util.Objects;

/** Asks for a fresh verification link when the first one expired or never arrived. */
public record ResendAccountVerificationCommand(EmailAddress email, String sourceIp) implements Command<Void> {
    public ResendAccountVerificationCommand {
        Objects.requireNonNull(email, "The email address is required");
        sourceIp = sourceIp == null ? null : sourceIp.strip();
    }
}
