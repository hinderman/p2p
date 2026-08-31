package com.project.backend.application.command;

import com.project.backend.application.dto.Command;
import com.project.backend.application.dto.StoredPaymentProof;
import com.project.backend.domain.valueobject.UserAccountId;

import java.util.Arrays;
import java.util.Objects;

public record UploadPaymentProofCommand(
        UserAccountId uploaderAccountId,
        String originalName,
        String declaredContentType,
        byte[] content) implements Command<StoredPaymentProof> {
    public UploadPaymentProofCommand {
        Objects.requireNonNull(uploaderAccountId, "The uploader account is required");
        Objects.requireNonNull(originalName, "The original file name is required");
        declaredContentType = declaredContentType == null ? "" : declaredContentType;
        content = Arrays.copyOf(Objects.requireNonNull(content, "The file content is required"), content.length);
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
