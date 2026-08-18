package com.project.backend.domain.event;

import com.project.backend.domain.valueobject.ReportedPaymentId;
import com.project.backend.domain.valueobject.LoanId;

import java.time.Instant;

public record PaymentSubmittedForReview(ReportedPaymentId reportedPaymentId, LoanId loanId, Instant occurredAt) implements DomainEvent {
}
