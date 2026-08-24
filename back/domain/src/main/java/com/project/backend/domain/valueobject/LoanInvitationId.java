package com.project.backend.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record LoanInvitationId(UUID value) implements ValueObject {
    public LoanInvitationId {
        Objects.requireNonNull(value, "The loan invitation identifier is required");
    }
}
