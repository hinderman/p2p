package com.project.backend.domain.identity;

import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.valueobject.EmailAddress;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Password strength rules for every use case that sets a password.
 *
 * <p>The rules follow NIST SP 800-63B: length is the primary control, and
 * composition rules ("one uppercase, one digit, one symbol") are deliberately
 * absent because they push people towards predictable substitutions without
 * adding real entropy. What is rejected instead is what actually makes a
 * password guessable: being short, being well known, being derived from the
 * address it protects, or repeating a handful of characters.
 *
 * <p>The well-known list is a small curated set, not a breach corpus. Checking
 * candidates against a full list — locally, or through the Have I Been Pwned
 * range API, which never receives the password — is the production upgrade and
 * would replace only {@link #isWellKnown(String)}.
 */
public final class PasswordPolicy {
    public static final int MINIMUM_LENGTH = 12;
    public static final int MAXIMUM_LENGTH = 128;

    private static final int MINIMUM_DISTINCT_CHARACTERS = 5;
    private static final int SIGNIFICANT_LOCAL_PART_LENGTH = 4;

    /** Bases that remain the most common even once a 12-character minimum is enforced. */
    private static final Set<String> WELL_KNOWN_BASES = Set.of(
            "password", "passw0rd", "contrasena", "contraseña", "qwerty", "asdfgh",
            "iloveyou", "letmein", "welcome", "admin123", "administrator", "12345678");

    private PasswordPolicy() {
    }

    /**
     * @throws DomainRuleViolation when the password does not satisfy the policy.
     *         The message states the single unmet rule so the caller can show it.
     */
    public static void validate(char[] password, EmailAddress email) {
        Objects.requireNonNull(password, "The password is required");
        Objects.requireNonNull(email, "The account email is required");

        if (password.length < MINIMUM_LENGTH || password.length > MAXIMUM_LENGTH) {
            throw new DomainRuleViolation(
                    "The password must have between %d and %d characters".formatted(MINIMUM_LENGTH, MAXIMUM_LENGTH));
        }
        if (isBlank(password)) {
            throw new DomainRuleViolation("The password cannot consist only of blank space");
        }
        if (distinctCharacters(password) < MINIMUM_DISTINCT_CHARACTERS) {
            throw new DomainRuleViolation(
                    "The password must use at least %d different characters".formatted(MINIMUM_DISTINCT_CHARACTERS));
        }

        String normalized = new String(password).toLowerCase(Locale.ROOT);
        if (isWellKnown(normalized)) {
            throw new DomainRuleViolation("The password is too common; choose one that is harder to guess");
        }
        if (containsEmailLocalPart(normalized, email)) {
            throw new DomainRuleViolation("The password cannot contain your email address");
        }
    }

    private static boolean isBlank(char[] password) {
        for (char character : password) {
            if (!Character.isWhitespace(character)) {
                return false;
            }
        }
        return true;
    }

    private static long distinctCharacters(char[] password) {
        return new String(password).chars().distinct().count();
    }

    private static boolean isWellKnown(String normalizedPassword) {
        return WELL_KNOWN_BASES.stream().anyMatch(normalizedPassword::contains);
    }

    private static boolean containsEmailLocalPart(String normalizedPassword, EmailAddress email) {
        String localPart = email.value().substring(0, email.value().indexOf('@'));
        return localPart.length() >= SIGNIFICANT_LOCAL_PART_LENGTH && normalizedPassword.contains(localPart);
    }
}
