package com.project.backend.infrastructure.security;

import com.project.backend.application.port.out.PasswordHashingPort;
import com.project.backend.domain.identity.PasswordHash;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.CharBuffer;
import java.util.Objects;

/** Creates Argon2id hashes using the same calibrated parameters as password verification. */
@Component
public final class Argon2idPasswordHasher implements PasswordHashingPort {
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 19_456;
    private static final int ITERATIONS = 2;

    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(
            SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_KIB, ITERATIONS);

    @Override
    public PasswordHash hash(char[] plainPassword) {
        Objects.requireNonNull(plainPassword, "The plain password is required");
        return new PasswordHash(encoder.encode(CharBuffer.wrap(plainPassword)));
    }
}
