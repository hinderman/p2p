package com.project.backend.application.dto;

import com.project.backend.domain.loan.LoanStatus;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.PersonId;

import java.time.Instant;
import java.util.Objects;

public record LoanDetail(
        LoanId loanId,
        PersonId counterpartyPersonId,
        LoanStatus status,
        Money originalPrincipal,
        Money outstandingBalance,
        Instant createdAt,
        LoanTermsDetail terms,
        PaymentPlanDetail paymentPlan) {
    public LoanDetail {
        Objects.requireNonNull(loanId, "The loan id is required");
        Objects.requireNonNull(counterpartyPersonId, "The counterparty is required");
        Objects.requireNonNull(status, "The loan status is required");
        Objects.requireNonNull(originalPrincipal, "The original principal is required");
        Objects.requireNonNull(outstandingBalance, "The outstanding balance is required");
        Objects.requireNonNull(createdAt, "The creation time is required");
        Objects.requireNonNull(terms, "The loan terms are required");
    }
}
