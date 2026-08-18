package com.project.backend.domain.exception;

/** Business-invariant violation that cannot be delegated to infrastructure. */
public final class DomainRuleViolation extends DomainException {

    public DomainRuleViolation(String message) {
        super(message);
    }
}
