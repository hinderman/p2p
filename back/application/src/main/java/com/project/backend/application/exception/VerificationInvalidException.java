package com.project.backend.application.exception;

/** Deliberately generic to avoid revealing whether a verification token ever existed. */
public final class VerificationInvalidException extends ApplicationException {
    public VerificationInvalidException() {
        super("The verification link is invalid, expired, or has already been used");
    }
}
