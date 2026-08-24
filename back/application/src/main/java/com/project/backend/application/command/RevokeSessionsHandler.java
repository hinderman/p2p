package com.project.backend.application.command;

import com.project.backend.application.port.in.command.CommandHandler;
import com.project.backend.application.port.out.AuthenticationSessionPort;
import com.project.backend.application.port.out.ClockPort;

import java.util.Objects;

public final class RevokeSessionsHandler implements CommandHandler<RevokeSessionsCommand, Void> {
    private final AuthenticationSessionPort sessions;
    private final ClockPort clock;

    public RevokeSessionsHandler(AuthenticationSessionPort sessions, ClockPort clock) {
        this.sessions = Objects.requireNonNull(sessions, "The authentication session port is required");
        this.clock = Objects.requireNonNull(clock, "The clock is required");
    }

    @Override
    public Void execute(RevokeSessionsCommand command) {
        sessions.revokeAllActiveSessions(command.accountId(), clock.now());
        return null;
    }
}
