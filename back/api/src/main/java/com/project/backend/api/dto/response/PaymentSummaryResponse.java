package com.project.backend.api.dto.response;

import com.project.backend.domain.payment.ReportedPaymentStatus;

import java.time.LocalDate;
import java.util.UUID;

public record PaymentSummaryResponse(
        UUID reportedPaymentId,
        UUID loanId,
        ReportedPaymentStatus status,
        MoneyResponse reportedAmount,
        MoneyResponse validatedAmount,
        LocalDate reportedPaymentDate) {
}
