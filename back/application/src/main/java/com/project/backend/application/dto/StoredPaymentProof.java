package com.project.backend.application.dto;

import java.util.Objects;
import java.util.UUID;

public record StoredPaymentProof(
        UUID storedObjectId,
        String originalName,
        String contentType,
        long sizeBytes,
        String sha256) {
    public StoredPaymentProof {
        Objects.requireNonNull(storedObjectId, "The stored object id is required");
        Objects.requireNonNull(originalName, "The original file name is required");
        Objects.requireNonNull(contentType, "The content type is required");
        Objects.requireNonNull(sha256, "The SHA-256 digest is required");
    }
}
