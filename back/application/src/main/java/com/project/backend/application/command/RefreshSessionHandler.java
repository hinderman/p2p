package com.project.backend.application.command;

import com.project.backend.application.dto.AuthenticatedSession;
import com.project.backend.application.exception.AuthenticationFailedException;
import com.project.backend.application.port.in.command.CommandHandler;
import com.project.backend.application.port.out.AuthenticationSessionPort;
import com.project.backend.application.port.out.ClockPort;

import java.util.Objects;

public final class RefreshSessionHandler implements CommandHandler<RefreshSessionCommand, AuthenticatedSession> {
    private final AuthenticationSessionPort sessions;
    private final ClockPort clock;

    public RefreshSessionHandler(AuthenticationSessionPort sessions, ClockPort clock) {
        this.sessions = Objects.requireNonNull(sessions, "The authentication session port is required");
        this.clock = Objects.requireNonNull(clock, "The clock is required");
    }

    @Override
    public AuthenticatedSession execute(RefreshSessionCommand command) {
        return sessions.rotateRefreshToken(command.refreshToken(), clock.now())
                .orElseThrow(AuthenticationFailedException::new);
    }
}
