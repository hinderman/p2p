package com.project.backend.application.port.out;

import com.project.backend.domain.event.DomainEvent;

import java.util.List;

/** Persists domain events in the same transaction as the aggregate change. */
public interface OutboxEventsPort {
    void enqueue(List<DomainEvent> domainEvents);
}
