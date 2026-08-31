package com.project.backend.domain.identity;

import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.valueobject.EmailAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {
    private static final EmailAddress EMAIL = new EmailAddress("carolina@example.com");

    @Test
    void accepts_a_long_passphrase_without_demanding_symbols_or_digits() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("mesa verde caliente".toCharArray(), EMAIL));
    }

    @Test
    void rejects_a_password_shorter_than_the_minimum() {
        assertThrows(DomainRuleViolation.class, () -> PasswordPolicy.validate("corta12345".toCharArray(), EMAIL));
    }

    @Test
    void rejects_a_password_longer_than_the_maximum() {
        char[] tooLong = new char[PasswordPolicy.MAXIMUM_LENGTH + 1];
        for (int index = 0; index < tooLong.length; index++) {
            tooLong[index] = (char) ('a' + (index % 26));
        }

        assertThrows(DomainRuleViolation.class, () -> PasswordPolicy.validate(tooLong, EMAIL));
    }

    /** Long enough, but with almost no material: "aaaaaaaaaaaa" must not pass on length alone. */
    @Test
    void rejects_a_password_built_from_too_few_distinct_characters() {
        assertThrows(DomainRuleViolation.class, () -> PasswordPolicy.validate("ababababababab".toCharArray(), EMAIL));
    }

    @Test
    void rejects_a_password_that_is_only_blank_space() {
        assertThrows(DomainRuleViolation.class, () -> PasswordPolicy.validate("               ".toCharArray(), EMAIL));
    }

    @Test
    void rejects_a_well_known_password_even_when_it_is_long_enough() {
        assertThrows(DomainRuleViolation.class, () -> PasswordPolicy.validate("MiPassword2026".toCharArray(), EMAIL));
        assertThrows(DomainRuleViolation.class, () -> PasswordPolicy.validate("contrasena-2026".toCharArray(), EMAIL));
    }

    /** A password derived from the address it protects is guessable by anyone who has the address. */
    @Test
    void rejects_a_password_containing_the_email_local_part() {
        assertThrows(DomainRuleViolation.class, () -> PasswordPolicy.validate("Carolina-2026-x".toCharArray(), EMAIL));
    }

    /** A very short local part would otherwise ban unrelated passwords that merely contain it. */
    @Test
    void ignores_a_local_part_too_short_to_be_meaningful() {
        assertDoesNotThrow(() -> PasswordPolicy.validate(
                "abc montaña serena".toCharArray(), new EmailAddress("abc@example.com")));
    }
}
