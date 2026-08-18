package com.project.backend.application.exception;

/**
 * Base de los errores propios de la capa de allocation.
 *
 * <p>Cubre fallos de orquestacion que no son violaciones de reglas de negocio
 * (eso es {@code DomainException}) ni fallos tecnicos: por ejemplo, que el
 * aggregate solicitado no exista, o que la operation no este permitida para el
 * usuario current.
 *
 * <p>La capa api las traduce a codigos HTTP.
 */
public abstract class ApplicationException extends RuntimeException {

    protected ApplicationException(String message) {
        super(message);
    }

    protected ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
