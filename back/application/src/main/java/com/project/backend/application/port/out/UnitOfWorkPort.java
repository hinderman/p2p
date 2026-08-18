package com.project.backend.application.port.out;

import java.util.function.Supplier;

/** Frontera transaccional independiente de Spring o JPA. */
public interface UnitOfWorkPort {
    <T> T execute(Supplier<T> operation);
}
