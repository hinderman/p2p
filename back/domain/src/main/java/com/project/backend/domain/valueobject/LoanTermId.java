package com.project.backend.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record LoanTermId(UUID value) implements ValueObject {
    public LoanTermId {
        Objects.requireNonNull(value, "The loan term identifier is required");
    }
}
