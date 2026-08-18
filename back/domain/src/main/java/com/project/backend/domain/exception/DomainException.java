package com.project.backend.domain.exception;

/**
 * Base class for domain business errors.
 *
 * <p>Representa la violacion de una invariante o de una regla de negocio, no un
 * fallo tecnico. Cada aggregate define sus propias subclases, por ejemplo
 * {@code SaldoInsuficienteException}.
 *
 * <p>Es responsabilidad de la capa api traducir estas excepciones al codigo HTTP
 * that applies; the domain has no knowledge of HTTP.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
