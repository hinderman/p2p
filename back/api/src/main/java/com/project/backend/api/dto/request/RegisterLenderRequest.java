package com.project.backend.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lender sign-up input.
 *
 * <p>The size bounds only reject obviously malformed input. Password strength is
 * a business rule enforced by the domain, so the caller is told which rule the
 * password failed rather than being handed a generic validation error.
 */
public record RegisterLenderRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotBlank @Size(max = 120) String firstName,
        @NotBlank @Size(max = 120) String lastName) {
}
