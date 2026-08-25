package com.project.backend.application.port.in.query;

import com.project.backend.application.dto.Query;
import com.project.backend.application.port.in.RequestHandler;

/** Lado de lectura de CQRS: no modifica aggregates ni publica domainEvents. */
@FunctionalInterface
public interface QueryHandler<Q extends Query<R>, R> extends RequestHandler<Q, R> {
}
