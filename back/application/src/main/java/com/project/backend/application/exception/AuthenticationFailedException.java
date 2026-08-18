package com.project.backend.application.exception;

/** Deliberately generic message that does not reveal whether an email exists. */
public final class AuthenticationFailedException extends ApplicationException {
    public AuthenticationFailedException() { super("Invalid credentials"); }
}
