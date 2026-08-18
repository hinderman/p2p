package com.project.backend.infrastructure.time;

import com.project.backend.application.port.out.ClockPort;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public final class SystemClockAdapter implements ClockPort {
    @Override
    public Instant now() {
        return Instant.now();
    }
}
