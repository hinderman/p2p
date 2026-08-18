package com.project.backend.application.dto;

import com.project.backend.domain.payment.ReportedPaymentStatus;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.ReportedPaymentId;
import com.project.backend.domain.valueobject.LoanId;

import java.time.LocalDate;

/** Payment read projection safe to expose to an authorized user. */
public record PaymentSummary(
        ReportedPaymentId reportedPaymentId,
        LoanId loanId,
        ReportedPaymentStatus status,
        Money reportedAmount,
        Money validatedAmount,
        LocalDate reportedPaymentDate) {
}
