package com.project.backend.domain.event;

import com.project.backend.domain.valueobject.LoanTermId;
import com.project.backend.domain.valueobject.LoanId;

import java.time.Instant;

public record LoanCreated(
        LoanId loanId,
        LoanTermId loanTermId,
        Instant occurredAt) implements DomainEvent {
}
