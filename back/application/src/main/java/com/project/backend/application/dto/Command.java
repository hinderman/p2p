package com.project.backend.application.dto;

/**
 * Intencion de <strong>modificar</strong> el status del sistema.
 *
 * <p>Commands use imperative names (for example, {@code CreateLoanCommand}) and are implemented as
 * {@code record} inmutable. Es un DTO propio de la capa application: no reutilizar
 * aqui los DTO HTTP de la capa api, para que un cambio en el contrato REST no
 * arrastre a los casos de uso.
 */
public interface Command {
}
