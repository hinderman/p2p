package com.project.backend.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.Objects;

@ConfigurationProperties(prefix = "app.payment-proof.storage")
public record PaymentProofStorageProperties(Path root) {
    public PaymentProofStorageProperties {
        Objects.requireNonNull(root, "The payment-proof storage root is required");
        root = root.toAbsolutePath().normalize();
    }
}
