package com.project.backend.application.dto;

import com.project.backend.domain.valueobject.UserAccountId;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record StoredObjectUpload(
        UUID id,
        UserAccountId uploadedBy,
        String originalName,
        String contentType,
        byte[] content,
        String sha256,
        Instant createdAt) {
    public StoredObjectUpload {
        Objects.requireNonNull(id, "The stored object id is required");
        Objects.requireNonNull(uploadedBy, "The uploader is required");
        Objects.requireNonNull(originalName, "The original file name is required");
        Objects.requireNonNull(contentType, "The content type is required");
        content = Arrays.copyOf(Objects.requireNonNull(content, "The content is required"), content.length);
        Objects.requireNonNull(sha256, "The SHA-256 digest is required");
        Objects.requireNonNull(createdAt, "The creation time is required");
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
