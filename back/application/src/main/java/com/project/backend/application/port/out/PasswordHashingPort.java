package com.project.backend.application.port.out;

import com.project.backend.domain.identity.PasswordHash;

/** Hashes a newly chosen password with the configured password-hashing policy. */
public interface PasswordHashingPort {

    PasswordHash hash(char[] plainPassword);
}
