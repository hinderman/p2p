package com.project.backend.application.command;

import com.project.backend.application.dto.Command;
import com.project.backend.domain.valueobject.EmailAddress;

import java.util.Objects;

public record SignInCommand(EmailAddress email, char[] password, String sourceIp) implements Command {
    public SignInCommand {
        Objects.requireNonNull(email, "El email es obligatorio");
        Objects.requireNonNull(password, "Password is required");
        password = password.clone();
        if (password.length == 0) throw new IllegalArgumentException("Password is required");
        sourceIp = sourceIp == null ? null : sourceIp.strip();
    }

    @Override
    public char[] password() { return password.clone(); }
}
