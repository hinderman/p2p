package com.project.backend.infrastructure.id;

import com.project.backend.application.port.out.UuidGeneratorPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public final class RandomUuidGenerator implements UuidGeneratorPort {
    @Override
    public UUID nextUuid() {
        return UUID.randomUUID();
    }
}
