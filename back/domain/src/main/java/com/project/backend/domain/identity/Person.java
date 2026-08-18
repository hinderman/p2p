package com.project.backend.domain.identity;

import com.project.backend.domain.entity.AggregateRoot;
import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.PersonId;

import java.time.Instant;
import java.util.Objects;

/** Person natural a la que se puede invitar antes de que exista una account. */
public final class Person extends AggregateRoot<PersonId> {
    private final EmailAddress primaryEmail;
    private PersonStatus status;
    private final Instant createdAt;

    private Person(PersonId id, EmailAddress primaryEmail, PersonStatus status, Instant createdAt) {
        super(id);
        this.primaryEmail = Objects.requireNonNull(primaryEmail, "El email principal es obligatorio");
        this.status = Objects.requireNonNull(status, "El status de la person es obligatorio");
        this.createdAt = Objects.requireNonNull(createdAt, "The creation time is required");
    }

    public static Person createPending(PersonId id, EmailAddress primaryEmail, Instant createdAt) {
        return new Person(id, primaryEmail, PersonStatus.PENDING, createdAt);
    }

    public static Person rehydrate(PersonId id, EmailAddress primaryEmail, PersonStatus status, Instant createdAt) {
        return new Person(id, primaryEmail, status, createdAt);
    }

    public EmailAddress primaryEmail() { return primaryEmail; }
    public PersonStatus status() { return status; }
    public Instant createdAt() { return createdAt; }

    public void activate() {
        if (status != PersonStatus.PENDING) {
            throw new DomainRuleViolation("Solo una person pendiente puede activarse");
        }
        status = PersonStatus.ACTIVE;
    }
}
