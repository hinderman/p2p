package com.project.backend.domain.event;

import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.PaymentPlanId;

import java.time.Instant;

public record PaymentPlanRecalculated(
        LoanId loanId,
        PaymentPlanId previousPlanId,
        PaymentPlanId newPlanId,
        Instant occurredAt) implements DomainEvent {
}
