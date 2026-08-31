package com.project.backend.api.dto.response;

import com.project.backend.domain.loan.LoanStatus;

import java.time.Instant;
import java.util.UUID;

public record LoanDetailResponse(
        UUID loanId,
        UUID counterpartyPersonId,
        LoanStatus status,
        MoneyResponse originalPrincipal,
        MoneyResponse outstandingBalance,
        Instant createdAt,
        LoanTermsDetailResponse terms,
        PaymentPlanDetailResponse paymentPlan) { }
