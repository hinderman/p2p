package com.project.backend.application.dto;

import com.project.backend.domain.loan.PaymentPlanReason;
import com.project.backend.domain.loan.PaymentPlanStatus;
import com.project.backend.domain.valueobject.PaymentPlanId;

import java.util.List;
import java.util.Objects;

public record PaymentPlanDetail(
        PaymentPlanId paymentPlanId,
        int versionNumber,
        PaymentPlanReason reason,
        PaymentPlanStatus status,
        List<InstallmentDetail> installments) {
    public PaymentPlanDetail {
        Objects.requireNonNull(paymentPlanId, "The payment plan id is required");
        Objects.requireNonNull(reason, "The payment plan reason is required");
        Objects.requireNonNull(status, "The payment plan status is required");
        installments = List.copyOf(Objects.requireNonNull(installments, "The installments are required"));
    }
}
