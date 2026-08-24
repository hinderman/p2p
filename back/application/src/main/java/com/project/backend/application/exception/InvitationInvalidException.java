package com.project.backend.application.exception;

/** Deliberately generic to avoid revealing invitation existence or status. */
public final class InvitationInvalidException extends ApplicationException {
    public InvitationInvalidException() {
        super("The invitation is invalid, expired, or has already been used");
    }
}
