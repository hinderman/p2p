package com.project.backend.application.command;

import com.project.backend.application.dto.Command;
import com.project.backend.domain.identity.PasswordPolicy;
import com.project.backend.domain.valueobject.EmailAddress;

import java.util.Arrays;
import java.util.Objects;

/**
 * Self-service intent to open a lender account.
 *
 * <p>The compact constructor only bounds the input so a malformed request cannot
 * reach expensive work. Password strength is a business rule and belongs to
 * {@link PasswordPolicy}, which the handler applies, so the caller receives the
 * specific unmet rule rather than a generic rejection.
 */
public record RegisterLenderCommand(
        EmailAddress email,
        char[] password,
        String firstName,
        String lastName,
        String sourceIp) implements Command<Void> {

    public RegisterLenderCommand {
        Objects.requireNonNull(email, "The email address is required");
        password = Objects.requireNonNull(password, "The password is required").clone();
        if (password.length == 0 || password.length > PasswordPolicy.MAXIMUM_LENGTH) {
            Arrays.fill(password, '\0');
            throw new IllegalArgumentException(
                    "The password must not exceed %d characters".formatted(PasswordPolicy.MAXIMUM_LENGTH));
        }
        firstName = requireBoundedName(firstName, "first name");
        lastName = requireBoundedName(lastName, "last name");
        sourceIp = sourceIp == null ? null : sourceIp.strip();
    }

    @Override
    public char[] password() {
        return password.clone();
    }

    private static String requireBoundedName(String value, String label) {
        String normalized = Objects.requireNonNull(value, "The %s is required".formatted(label)).strip();
        if (normalized.isEmpty() || normalized.length() > 120) {
            throw new IllegalArgumentException("The %s is invalid".formatted(label));
        }
        return normalized;
    }
}
