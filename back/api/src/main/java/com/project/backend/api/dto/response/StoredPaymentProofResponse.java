package com.project.backend.api.dto.response;

import com.project.backend.application.dto.StoredPaymentProof;

import java.util.UUID;

public record StoredPaymentProofResponse(
        UUID storedObjectId,
        String originalName,
        String contentType,
        long sizeBytes,
        String sha256,
        String scanStatus) {
    public static StoredPaymentProofResponse from(StoredPaymentProof proof) {
        return new StoredPaymentProofResponse(proof.storedObjectId(), proof.originalName(), proof.contentType(),
                proof.sizeBytes(), proof.sha256(), "SAFE");
    }
}
