package com.project.backend.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record ReportedPaymentId(UUID value) implements ValueObject {
    public ReportedPaymentId {
        Objects.requireNonNull(value, "El identificador de payment es obligatorio");
    }
}
