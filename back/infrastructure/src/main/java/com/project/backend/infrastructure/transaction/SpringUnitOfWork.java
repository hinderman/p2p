package com.project.backend.infrastructure.transaction;

import com.project.backend.application.port.out.UnitOfWorkPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.function.Supplier;

@Component
public final class SpringUnitOfWork implements UnitOfWorkPort {
    private final TransactionTemplate transactionTemplate;

    public SpringUnitOfWork(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public <T> T execute(Supplier<T> operation) {
        Objects.requireNonNull(operation, "Transaction operation is required");
        return transactionTemplate.execute(status -> operation.get());
    }
}
