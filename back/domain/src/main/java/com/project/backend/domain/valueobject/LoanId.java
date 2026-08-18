package com.project.backend.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record LoanId(UUID value) implements ValueObject {
    public LoanId {
        Objects.requireNonNull(value, "The loan identifier is required");
    }
}
