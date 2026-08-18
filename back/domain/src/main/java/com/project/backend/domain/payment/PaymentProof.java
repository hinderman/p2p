package com.project.backend.domain.payment;

import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.valueobject.ValueObject;

import java.util.Objects;
import java.util.UUID;

/** Referencia inmutable al archivo ya validado por el adaptador de almacenamiento. */
public record PaymentProof(UUID storedObjectId, String sha256) implements ValueObject {
    public PaymentProof {
        Objects.requireNonNull(storedObjectId, "El archivo del proof es obligatorio");
        Objects.requireNonNull(sha256, "El hash del proof es obligatorio");
        sha256 = sha256.toLowerCase();
        if (!sha256.matches("^[0-9a-f]{64}$")) {
            throw new DomainRuleViolation("The proof must have a valid SHA-256 hash");
        }
    }
}
