package com.project.backend.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record PaymentPlanId(UUID value) implements ValueObject {
    public PaymentPlanId {
        Objects.requireNonNull(value, "El identificador de paymentPlan de payment es obligatorio");
    }
}
