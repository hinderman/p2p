package com.project.backend.application.dto;

import com.project.backend.domain.loan.LoanStatus;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.LoanId;

import java.time.Instant;

/** Read projection; it does not expose the domain aggregate. */
public record LoanSummary(
        LoanId loanId,
        PersonId counterpartyPersonId,
        LoanStatus status,
        Money originalPrincipal,
        Money outstandingBalance,
        Instant createdAt) {
}
