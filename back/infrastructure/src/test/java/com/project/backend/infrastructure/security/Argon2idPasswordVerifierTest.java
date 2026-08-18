package com.project.backend.infrastructure.security;

import com.project.backend.domain.identity.PasswordHash;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Argon2idPasswordVerifierTest {

    @Test
    void verifies_a_valid_argon2id_password_hash_without_accepting_another_password() {
        Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
        PasswordHash hash = new PasswordHash(encoder.encode("correct horse battery staple"));
        Argon2idPasswordVerifier verifier = new Argon2idPasswordVerifier();

        assertTrue(verifier.matches("correct horse battery staple".toCharArray(), hash));
        assertFalse(verifier.matches("incorrect password".toCharArray(), hash));
    }
}
