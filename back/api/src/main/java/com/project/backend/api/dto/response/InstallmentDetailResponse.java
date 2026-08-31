package com.project.backend.api.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record InstallmentDetailResponse(
        UUID installmentId,
        int number,
        LocalDate dueDate,
        MoneyResponse agreedPrincipal,
        MoneyResponse agreedInterest,
        MoneyResponse agreedFee,
        MoneyResponse agreedTotal,
        MoneyResponse paidPrincipal,
        MoneyResponse paidInterest,
        MoneyResponse paidFee,
        MoneyResponse paidTotal,
        MoneyResponse outstandingPrincipal,
        MoneyResponse outstandingInterest,
        MoneyResponse outstandingFee,
        MoneyResponse outstandingTotal) { }
