package com.project.backend.application.port.in.query;

import com.project.backend.application.dto.Query;
import com.project.backend.application.port.in.UseCase;

/** Lado de lectura de CQRS: no modifica aggregates ni publica domainEvents. */
@FunctionalInterface
public interface QueryHandler<Q extends Query, R> extends UseCase<Q, R> {
}
