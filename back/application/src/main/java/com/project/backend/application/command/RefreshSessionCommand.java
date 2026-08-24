package com.project.backend.application.command;

import com.project.backend.application.dto.Command;

import java.util.Objects;

public record RefreshSessionCommand(String refreshToken) implements Command {
    public RefreshSessionCommand {
        refreshToken = Objects.requireNonNull(refreshToken, "The refresh token is required").strip();
        if (refreshToken.isEmpty()) {
            throw new IllegalArgumentException("The refresh token is required");
        }
    }
}
