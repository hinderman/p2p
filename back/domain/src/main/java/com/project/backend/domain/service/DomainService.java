package com.project.backend.domain.service;

/**
 * Marks a domain service.
 *
 * <p>A domain service exists only when a business rule
 * <strong>no pertenece de forma natural a ninguna entidad ni value object</strong>,
 * tipicamente porque coordina varios aggregates (por ejemplo, una politica de
 * precios que cruza {@code Pedido} y {@code Cliente}).
 *
 * <p>Do not confuse this with an application service: a domain service contains
 * <em>reglas de negocio</em>, no orquestacion. Si solo coordina llamadas, va en
 * la capa application.
 *
 * <p>Es Java puro y sin status.
 */
public interface DomainService {
}
