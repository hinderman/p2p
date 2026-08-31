package com.project.backend.application.dto;

import com.project.backend.domain.valueobject.UserAccountId;

import java.util.Objects;
import java.util.UUID;

public record StoredObjectRecord(
        UUID id,
        UserAccountId uploadedBy,
        String sha256,
        StoredObjectScanStatus scanStatus,
        boolean attached) {
    public StoredObjectRecord {
        Objects.requireNonNull(id, "The stored object id is required");
        Objects.requireNonNull(uploadedBy, "The uploader is required");
        Objects.requireNonNull(sha256, "The SHA-256 digest is required");
        Objects.requireNonNull(scanStatus, "The scan status is required");
    }
}
