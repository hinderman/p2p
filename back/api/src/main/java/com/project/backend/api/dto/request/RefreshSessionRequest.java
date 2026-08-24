package com.project.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshSessionRequest(@NotBlank @Size(max = 512) String refreshToken) {
}
