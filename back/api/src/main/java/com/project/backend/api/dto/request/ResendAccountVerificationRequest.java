package com.project.backend.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Requests a replacement verification link for an address awaiting confirmation. */
public record ResendAccountVerificationRequest(@NotBlank @Email @Size(max = 254) String email) {
}
