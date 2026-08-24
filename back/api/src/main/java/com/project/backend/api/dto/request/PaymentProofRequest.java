package com.project.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/** Reference to a previously uploaded and malware-scanned payment proof. */
public record PaymentProofRequest(
        @NotNull UUID storedObjectId,
        @NotBlank @Pattern(regexp = "^[A-Fa-f0-9]{64}$") String sha256) {
}
