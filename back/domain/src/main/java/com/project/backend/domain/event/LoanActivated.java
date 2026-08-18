package com.project.backend.domain.event;

import com.project.backend.domain.valueobject.LoanTermId;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.PaymentPlanId;

import java.time.Instant;

public record LoanActivated(
        LoanId loanId,
        LoanTermId loanTermId,
        PaymentPlanId paymentPlanId,
        Instant occurredAt) implements DomainEvent {
}
