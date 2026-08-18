package com.project.backend.domain.repository;

import com.project.backend.domain.entity.AggregateRoot;

import java.util.Optional;

/**
 * Puerto de persistencia de una raiz de aggregate.
 *
 * <p>The <strong>interface</strong> lives in the domain; the <strong>implementation</strong>
 * lives in infrastructure. This inversion prevents the domain from knowing
 * nada de JPA ni de PostgreSQL.
 *
 * <p>Solo las raices de aggregate tienen repositorio: las entidades internas se
 * alcanzan navegando desde su raiz.
 *
 * <p>Esta interfaz generica cubre las operationes habituales. Cada aggregate puede
 * declarar la suya propia y exponer solo lo que de verdad necesita, mas sus
 * consultas especificas.
 *
 * @param <T>  type de la raiz de aggregate
 * @param <ID> type de su identificador
 */
public interface Repository<T extends AggregateRoot<ID>, ID> {

    Optional<T> findById(ID id);

    T save(T aggregate);

    void delete(ID id);

    boolean exists(ID id);
}
