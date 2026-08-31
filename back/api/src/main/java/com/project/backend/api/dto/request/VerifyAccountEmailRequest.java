package com.project.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The token arrives in a link fragment and is submitted only in this body. */
public record VerifyAccountEmailRequest(@NotBlank @Size(max = 512) String verificationToken) {
}
