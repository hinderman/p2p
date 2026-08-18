package com.project.backend.domain.valueobject;

import com.project.backend.domain.exception.DomainRuleViolation;

import java.util.Locale;
import java.util.Objects;

/** Normalized email address for identity and invitations. */
public record EmailAddress(String value) implements ValueObject {
    public EmailAddress {
        Objects.requireNonNull(value, "El email es obligatorio");
        value = value.strip().toLowerCase(Locale.ROOT);
        if (value.length() > 254 || !value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new DomainRuleViolation("The email address is invalid");
        }
    }
}
