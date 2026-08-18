package com.project.backend.domain.event;

import java.time.Instant;

/**
 * Something meaningful that has already happened in the domain.
 *
 * <p>Se nombra siempre en <strong>pasado</strong> ({@code PedidoConfirmado},
 * {@code UsuarioRegistrado}) porque describe un hecho consumado, no una orden.
 *
 * <p>Los domainEvents son inmutables: lo natural es implementarlos como {@code record}.
 */
public interface DomainEvent {

    /** Momento en que ocurrio el hecho. */
    Instant occurredAt();
}
