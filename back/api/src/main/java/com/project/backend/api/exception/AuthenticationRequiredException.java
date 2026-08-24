package com.project.backend.api.exception;

/** Raised when a protected endpoint has no verified HTTP principal. */
public final class AuthenticationRequiredException extends RuntimeException {
    public AuthenticationRequiredException() {
        super("Authentication is required");
    }
}
