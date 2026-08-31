package com.project.backend.application.port.out;

import com.project.backend.application.dto.OutboundEmailMessage;
import com.project.backend.domain.event.DomainEvent;

import java.time.Instant;
import java.util.List;

/** Persists domain events in the same transaction as the aggregate change. */
public interface OutboxEventsPort {
    void enqueue(List<DomainEvent> domainEvents);

    /**
     * Stores an encrypted delivery request atomically with the change that caused
     * it, so a message is never sent for a transaction that later rolls back.
     */
    void enqueueEmail(OutboundEmailMessage message, Instant occurredAt);
}
