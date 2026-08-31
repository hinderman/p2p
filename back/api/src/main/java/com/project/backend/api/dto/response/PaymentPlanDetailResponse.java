package com.project.backend.api.dto.response;

import com.project.backend.domain.loan.PaymentPlanReason;
import com.project.backend.domain.loan.PaymentPlanStatus;

import java.util.List;
import java.util.UUID;

public record PaymentPlanDetailResponse(
        UUID paymentPlanId,
        int versionNumber,
        PaymentPlanReason reason,
        PaymentPlanStatus status,
        List<InstallmentDetailResponse> installments) { }
