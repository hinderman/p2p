package com.project.backend.application.command;

import com.project.backend.application.dto.Command;
import com.project.backend.domain.valueobject.UserAccountId;

import java.util.Objects;

/** User intent to sign out from every active device. */
public record RevokeSessionsCommand(UserAccountId accountId) implements Command<Void> {
    public RevokeSessionsCommand {
        Objects.requireNonNull(accountId, "The user account is required");
    }
}
