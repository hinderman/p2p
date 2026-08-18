package com.project.backend.infrastructure.security;

import com.project.backend.application.port.out.PasswordVerifierPort;
import com.project.backend.domain.identity.PasswordHash;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.CharBuffer;
import java.util.Objects;

/** Verifies password hashes encoded with Argon2id. */
@Component
public final class Argon2idPasswordVerifier implements PasswordVerifierPort {
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 19_456;
    private static final int ITERATIONS = 2;

    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(
            SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_KIB, ITERATIONS);

    @Override
    public boolean matches(char[] plainPassword, PasswordHash storedHash) {
        Objects.requireNonNull(plainPassword, "Plain password is required");
        Objects.requireNonNull(storedHash, "Stored password hash is required");
        return encoder.matches(CharBuffer.wrap(plainPassword), storedHash.value());
    }
}
