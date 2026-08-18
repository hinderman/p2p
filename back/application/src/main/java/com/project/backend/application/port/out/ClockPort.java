package com.project.backend.application.port.out;

import java.time.Instant;

public interface ClockPort {
    Instant now();
}
