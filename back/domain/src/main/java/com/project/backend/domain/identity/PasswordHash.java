package com.project.backend.domain.identity;

import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.valueobject.ValueObject;

import java.util.Objects;

/** Opaque hash produced and verified by an infrastructure cryptographic port. */
public record PasswordHash(String value) implements ValueObject {
    public PasswordHash {
        Objects.requireNonNull(value, "The password hash is required");
        if (value.isBlank() || value.length() > 255) {
            throw new DomainRuleViolation("The password hash is invalid");
        }
    }
}
