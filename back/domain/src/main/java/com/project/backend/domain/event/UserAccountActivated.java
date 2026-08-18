package com.project.backend.domain.event;

import com.project.backend.domain.valueobject.UserAccountId;

import java.time.Instant;

public record UserAccountActivated(UserAccountId userAccountId, Instant occurredAt) implements DomainEvent {
}
