package com.project.backend.domain.identity;

import com.project.backend.domain.entity.AggregateRoot;
import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.PersonId;

import java.time.Instant;
import java.util.Objects;

/** Person natural a la que se puede invitar antes de que exista una account. */
public final class Person extends AggregateRoot<PersonId> {
    private static final int MAXIMUM_NAME_LENGTH = 120;

    private final EmailAddress primaryEmail;
    private final Instant createdAt;
    private PersonStatus status;
    /** Unknown until the person registers: an invited payer is created from an email alone. */
    private String firstName;
    private String lastName;

    private Person(
            PersonId id,
            EmailAddress primaryEmail,
            String firstName,
            String lastName,
            PersonStatus status,
            Instant createdAt) {
        super(id);
        this.primaryEmail = Objects.requireNonNull(primaryEmail, "El email principal es obligatorio");
        this.status = Objects.requireNonNull(status, "El status de la person es obligatorio");
        this.createdAt = Objects.requireNonNull(createdAt, "The creation time is required");
        this.firstName = firstName;
        this.lastName = lastName;
    }

    /** An invited counterparty: known only by the email the inviter typed. */
    public static Person createPending(PersonId id, EmailAddress primaryEmail, Instant createdAt) {
        return new Person(id, primaryEmail, null, null, PersonStatus.PENDING, createdAt);
    }

    /** A person who signed up: named, but pending until the email is verified. */
    public static Person register(
            PersonId id, EmailAddress primaryEmail, String firstName, String lastName, Instant createdAt) {
        return new Person(id, primaryEmail, requireName(firstName, "first name"), requireName(lastName, "last name"),
                PersonStatus.PENDING, createdAt);
    }

    public static Person rehydrate(
            PersonId id,
            EmailAddress primaryEmail,
            String firstName,
            String lastName,
            PersonStatus status,
            Instant createdAt) {
        return new Person(id, primaryEmail, firstName, lastName, status, createdAt);
    }

    public EmailAddress primaryEmail() { return primaryEmail; }
    public PersonStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public String firstName() { return firstName; }
    public String lastName() { return lastName; }

    /** Names an existing person, typically one created earlier from an invitation. */
    public void identify(String firstName, String lastName) {
        this.firstName = requireName(firstName, "first name");
        this.lastName = requireName(lastName, "last name");
    }

    public void activate() {
        if (status != PersonStatus.PENDING) {
            throw new DomainRuleViolation("Solo una person pendiente puede activarse");
        }
        status = PersonStatus.ACTIVE;
    }

    private static String requireName(String value, String label) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty() || normalized.length() > MAXIMUM_NAME_LENGTH) {
            throw new DomainRuleViolation(
                    "The %s must have between 1 and %d characters".formatted(label, MAXIMUM_NAME_LENGTH));
        }
        return normalized;
    }
}
