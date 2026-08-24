package com.project.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PayerOnboardingRequest(
        @NotBlank @Size(max = 512) String invitationToken,
        @NotBlank @Size(min = 12, max = 128) String password) {
}
