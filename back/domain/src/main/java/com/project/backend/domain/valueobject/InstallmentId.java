package com.project.backend.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record InstallmentId(UUID value) implements ValueObject {
    public InstallmentId {
        Objects.requireNonNull(value, "El identificador de installment es obligatorio");
    }
}
