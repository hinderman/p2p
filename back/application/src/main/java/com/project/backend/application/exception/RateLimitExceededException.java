package com.project.backend.application.exception;

public final class RateLimitExceededException extends ApplicationException {
    public RateLimitExceededException() {
        super("Too many sign-in attempts. Try again later.");
    }
}
