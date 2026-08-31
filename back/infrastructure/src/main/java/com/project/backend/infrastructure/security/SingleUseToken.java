package com.project.backend.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates the one-time tokens that travel by email and the digests that stand
 * in for them in the database.
 *
 * <p>256 bits of entropy makes guessing infeasible, which is what lets the
 * digest be a plain SHA-256: unlike a password, there is no low-entropy secret
 * for an attacker holding the table to brute-force back.
 */
public final class SingleUseToken {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private SingleUseToken() {
    }

    /** URL-safe so the value survives a link fragment without escaping. */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Lowercase hexadecimal, matching the {@code CHAR(64)} token-hash columns. */
    public static String digest(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
