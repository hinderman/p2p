package com.project.backend.application.port.out;

import com.project.backend.domain.identity.PasswordHash;

public interface PasswordVerifierPort {
    boolean matches(char[] plainPassword, PasswordHash storedHash);
}
