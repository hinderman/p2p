package com.project.backend.domain.event;

import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.ReportedPaymentId;
import com.project.backend.domain.valueobject.LoanId;

import java.time.Instant;

public record PaymentApproved(
        ReportedPaymentId reportedPaymentId,
        LoanId loanId,
        Money validatedAmount,
        Instant occurredAt) implements DomainEvent {
}
