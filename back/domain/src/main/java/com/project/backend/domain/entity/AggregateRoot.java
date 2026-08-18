package com.project.backend.domain.entity;

import com.project.backend.domain.event.DomainEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Raiz de aggregate: la unica entidad de un aggregate accesible desde fuera.
 *
 * <p>Marca la frontera de consistencia transaccional. Toda modificacion del
 * aggregate entra por aqui, y solo las raices de aggregate tienen repositorio.
 *
 * <p>Accumulates domain events produced while business rules are applied.
 * The infrastructure layer retrieves them with {@link #pullEvents()} after
 * de persistir, y los publica.
 *
 * @param <ID> type del identificador
 */
public abstract class AggregateRoot<ID> extends BaseEntity<ID> {

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected AggregateRoot(ID id) {
        super(id);
    }

    /** Registra un event ocurrido dentro del aggregate. */
    protected void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    /** Eventos pendientes, en solo lectura. */
    public List<DomainEvent> domainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /** Devuelve los domainEvents pendientes y vacia el buffer. */
    public List<DomainEvent> pullEvents() {
        List<DomainEvent> copy = List.copyOf(domainEvents);
        domainEvents.clear();
        return copy;
    }
}
