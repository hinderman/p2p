package com.project.backend.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Identity of a natural person within the domain. */
public record PersonId(UUID value) implements ValueObject {
    public PersonId {
        Objects.requireNonNull(value, "El identificador de person es obligatorio");
    }
}
