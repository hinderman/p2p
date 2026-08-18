/**
 * Exception translation to HTTP responses.
 *
 * <p>Contains the {@code @RestControllerAdvice} that converts {@code DomainException}
 * and {@code ApplicationException} into status codes and error bodies, preferably
 * {@code ProblemDetail} (RFC 7807).
 *
 * <p>Centralizing translation here lets the domain and application layers throw
 * business-language exceptions without knowing HTTP.
 */
package com.project.backend.api.exception;
