package com.project.backend.application.port.out;

import com.project.backend.application.dto.InvitationEmailMessage;
import com.project.backend.domain.event.DomainEvent;

import java.time.Instant;
import java.util.List;

/** Persists domain events in the same transaction as the aggregate change. */
public interface OutboxEventsPort {
    void enqueue(List<DomainEvent> domainEvents);

    /** Stores an encrypted one-time invitation delivery request atomically with the loan proposal. */
    void enqueueInvitationEmail(InvitationEmailMessage invitation, Instant occurredAt);
}
