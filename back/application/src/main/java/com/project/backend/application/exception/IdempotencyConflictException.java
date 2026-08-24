package com.project.backend.application.exception;

/** A client reused an idempotency key with a different payment submission. */
public final class IdempotencyConflictException extends ApplicationException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
